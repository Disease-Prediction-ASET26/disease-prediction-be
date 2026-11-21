package com.diseaseprediction.security.hanko;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

class IdentityQuarantineFilterTest {
    private static final String BLOCKED = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";
    private static final String ALLOWED = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb";

    @TempDir Path temporary;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enabledFailsClosedWhenTheFileIsMissingEmptyOrMalformed() throws Exception {
        assertThatThrownBy(
                        () ->
                                new IdentityQuarantineFilter(
                                        new IdentityQuarantineProperties(true, null)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(
                        () ->
                                new IdentityQuarantineFilter(
                                        new IdentityQuarantineProperties(
                                                true, temporary.resolve("missing").toString())))
                .isInstanceOf(IllegalStateException.class);
        Path file = temporary.resolve("subjects");
        Files.writeString(file, "\n");
        assertThatThrownBy(
                        () ->
                                new IdentityQuarantineFilter(
                                        new IdentityQuarantineProperties(true, file.toString())))
                .isInstanceOf(IllegalStateException.class);
        Files.writeString(file, BLOCKED + "\nnot-a-uuid\n");
        assertThatThrownBy(
                        () ->
                                new IdentityQuarantineFilter(
                                        new IdentityQuarantineProperties(true, file.toString())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("not-a-uuid")
                .hasMessageNotContaining(BLOCKED);
    }

    @Test
    void rejectsBlockedAndMalformedSubjectsAndPassesOthers() throws Exception {
        Path file = temporary.resolve("subjects");
        Files.writeString(file, BLOCKED + "\n");
        var filter =
                new IdentityQuarantineFilter(
                        new IdentityQuarantineProperties(true, file.toString()));
        MockHttpServletResponse denied = run(filter, BLOCKED);
        assertThat(denied.getStatus()).isEqualTo(403);
        assertThat(denied.getContentAsString())
                .contains("account-quarantined")
                .doesNotContain(BLOCKED);
        assertThat(run(filter, "not-a-uuid").getStatus()).isEqualTo(403);
        assertThat(run(filter, ALLOWED).getStatus()).isEqualTo(200);
    }

    @Test
    void disabledNeedsNoFileAndDoesNotBlockSubjects() throws Exception {
        var filter = new IdentityQuarantineFilter(new IdentityQuarantineProperties(false, null));
        assertThat(run(filter, BLOCKED).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse run(IdentityQuarantineFilter filter, String subject)
            throws Exception {
        Jwt jwt =
                Jwt.withTokenValue("token")
                        .header("alg", "RS256")
                        .subject(subject)
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest(), response, new MockFilterChain());
        return response;
    }
}
