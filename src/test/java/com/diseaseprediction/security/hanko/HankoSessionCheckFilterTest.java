package com.diseaseprediction.security.hanko;

import static org.assertj.core.api.Assertions.assertThat;

import com.diseaseprediction.security.ProblemAuthenticationEntryPoint;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Cache lifetime of the session check: at most the TTL and never past the token's expiry. */
class HankoSessionCheckFilterTest {

    private static final Duration TTL = Duration.ofSeconds(60);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-15T10:00:00Z"));
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean hankoSaysValid = true;

    private final HankoSessionCheckFilter filter =
            new HankoSessionCheckFilter(
                    token -> {
                        calls.incrementAndGet();
                        return hankoSaysValid;
                    },
                    new ProblemAuthenticationEntryPoint(),
                    TTL,
                    clock);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reusesTheAnswerWithinTheTtlAndAsksAgainAfterwards() throws Exception {
        Jwt jwt = jwt("session-1", clock.instant().plus(Duration.ofHours(1)));

        assertThat(run(jwt)).isEqualTo(200);
        clock.advance(Duration.ofSeconds(59));
        assertThat(run(jwt)).isEqualTo(200);
        assertThat(calls).hasValue(1);

        clock.advance(Duration.ofSeconds(2));
        hankoSaysValid = false; // logged out meanwhile
        assertThat(run(jwt)).isEqualTo(401);
        assertThat(calls).hasValue(2);
    }

    @Test
    void neverCachesPastTheTokenExpiry() throws Exception {
        Jwt jwt = jwt("session-2", clock.instant().plus(Duration.ofSeconds(10)));

        run(jwt);
        clock.advance(Duration.ofSeconds(11));
        run(jwt);

        assertThat(calls).hasValue(2);
    }

    @Test
    void tokenWithoutSessionIdIsRejectedWithoutAskingHanko() throws Exception {
        Jwt jwt = jwt(null, clock.instant().plus(Duration.ofHours(1)));

        assertThat(run(jwt)).isEqualTo(401);
        assertThat(calls).hasValue(0);
    }

    @Test
    void requestsWithoutAJwtPassThrough() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(calls).hasValue(0);
    }

    private int run(Jwt jwt) throws Exception {
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        return response.getStatus();
    }

    private Jwt jwt(String sessionId, Instant expiresAt) {
        Jwt.Builder builder =
                Jwt.withTokenValue("token-value")
                        .header("alg", "RS256")
                        .subject("user-1")
                        .issuedAt(expiresAt.minus(Duration.ofHours(1)))
                        .expiresAt(expiresAt);
        if (sessionId != null) {
            builder.claim("session_id", sessionId);
        }
        return builder.build();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }
}
