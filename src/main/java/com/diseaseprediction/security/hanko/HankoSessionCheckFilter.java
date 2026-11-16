package com.diseaseprediction.security.hanko;

import com.diseaseprediction.security.ProblemResponseWriter;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Second step of the session check (MDD Iteration 6). Runs after Spring Security verified the JWT
 * offline and asks Hanko whether the session is still active, so logout and revocation take effect
 * before the token expires.
 *
 * <p>Results are cached per {@code session_id} claim for {@code sessionCheckTtl}, never past the
 * token's expiry. If Hanko cannot be reached, the request is refused with 503: the API never falls
 * back to trusting the token alone. The session token is never logged.
 */
public class HankoSessionCheckFilter extends OncePerRequestFilter {

    static final String SESSION_ID_CLAIM = "session_id";

    private static final Logger log = LoggerFactory.getLogger(HankoSessionCheckFilter.class);

    private final HankoSessionClient sessionClient;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final Duration ttl;
    private final Clock clock;
    private final Cache<String, CachedCheck> cache;

    public HankoSessionCheckFilter(
            HankoSessionClient sessionClient,
            AuthenticationEntryPoint authenticationEntryPoint,
            Duration ttl,
            Clock clock) {
        this.sessionClient = sessionClient;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.ttl = ttl;
        this.clock = clock;
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(10_000)
                        // Same time source as the expiry calculation below.
                        .ticker(() -> Duration.between(Instant.EPOCH, clock.instant()).toNanos())
                        .expireAfter(
                                Expiry.creating((String key, CachedCheck check) -> lifetime(check)))
                        .build();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            chain.doFilter(request, response);
            return;
        }
        Jwt jwt = jwtAuthentication.getToken();
        String sessionId = jwt.getClaimAsString(SESSION_ID_CLAIM);
        if (sessionId == null || sessionId.isBlank()) {
            reject(request, response, "The token has no session_id claim.");
            return;
        }

        boolean valid;
        CachedCheck cached = cache.getIfPresent(sessionId);
        if (cached != null) {
            valid = cached.valid();
        } else {
            try {
                valid = sessionClient.isSessionValid(jwt.getTokenValue());
            } catch (IdentityProviderUnavailableException exception) {
                log.warn("Refusing request: {}", exception.getMessage());
                SecurityContextHolder.clearContext();
                ProblemResponseWriter.write(
                        response,
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "identity-unavailable",
                        "The identity service could not confirm the session. Try again shortly.");
                return;
            }
            cache.put(sessionId, new CachedCheck(valid, expiryOf(jwt)));
        }

        if (!valid) {
            reject(request, response, "The session has ended.");
            return;
        }
        chain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String reason)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(
                request, response, new InvalidBearerTokenException(reason));
    }

    private Instant expiryOf(Jwt jwt) {
        Instant now = clock.instant();
        Instant byTtl = now.plus(ttl);
        Instant expiresAt = jwt.getExpiresAt();
        return expiresAt != null && expiresAt.isBefore(byTtl) ? expiresAt : byTtl;
    }

    private Duration lifetime(CachedCheck check) {
        Duration remaining = Duration.between(clock.instant(), check.until());
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }

    private record CachedCheck(boolean valid, Instant until) {}
}
