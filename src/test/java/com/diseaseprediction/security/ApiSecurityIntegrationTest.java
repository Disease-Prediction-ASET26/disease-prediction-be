package com.diseaseprediction.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diseaseprediction.security.FakeHanko.SessionMode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Duration;
import java.time.Instant;

/**
 * MDD Iteration 6, Step 5: the token checks. Every rejected token gets 401 with {@code
 * WWW-Authenticate: Bearer}; the session check with Hanko fails closed.
 */
@ExtendWith(OutputCaptureExtension.class)
class ApiSecurityIntegrationTest extends AuthenticatedApiTest {

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("unauthorized"))
                .andExpect(jsonPath("$.type").value("urn:pocket-doc:problem:unauthorized"));
    }

    @Test
    void assessmentsAreNotPublicAnymore() throws Exception {
        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"freeText\":\"cough\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        // No profile yet, so 404 proves the request got past authentication.
        mockMvc.perform(
                        get("/api/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(TestTokens.token().build())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("profile-not-found"));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String token =
                TestTokens.token().expiresAt(Instant.now().minus(Duration.ofMinutes(5))).build();
        assertRejected(token);
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() throws Exception {
        assertRejected(TestTokens.token().issuer("https://identity-dev.pocket-doc.app").build());
    }

    @Test
    void tokenForAnotherAudienceIsRejected() throws Exception {
        // For example a production token sent to dev.
        assertRejected(TestTokens.token().audience("pocket-doc-prod").build());
    }

    @Test
    void tokenSignedWithAnUnknownKeyIsRejected() throws Exception {
        assertRejected(TestTokens.token().signedWithUnknownKey().build());
    }

    @Test
    void hmacSignedTokenIsRejected() throws Exception {
        assertRejected(TestTokens.token().buildHs256());
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        assertRejected(TestTokens.token().buildUnsigned());
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        assertRejected("not-a-jwt");
    }

    @Test
    void endedSessionIsRejected() throws Exception {
        hanko.sessionMode(SessionMode.INVALID);
        assertRejected(TestTokens.token().build());
    }

    @Test
    void unreachableHankoRefusesTheRequestWithoutLeakingTheToken(CapturedOutput output)
            throws Exception {
        hanko.sessionMode(SessionMode.ERROR);
        String token = TestTokens.token().build();

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("identity-unavailable"));

        assertThat(output).contains("Hanko session check failed");
        assertThat(output).doesNotContain(token);
    }

    @Test
    void sessionCheckIsCachedPerSession() throws Exception {
        TestTokens session = TestTokens.token();
        String token = session.build();

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isNotFound());
        }

        assertThat(hanko.validatedTokens()).hasSize(1);
        assertThat(hanko.validatedTokens().getFirst())
                .contains("\"session_token\":\"" + token + "\"");
    }

    @Test
    void anErrorIsNotCachedSoTheNextRequestAsksAgain() throws Exception {
        String token = TestTokens.token().build();
        hanko.sessionMode(SessionMode.ERROR);
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isServiceUnavailable());

        hanko.sessionMode(SessionMode.VALID);
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound());
        assertThat(hanko.validatedTokens()).hasSize(2);
    }

    private void assertRejected(String token) throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }
}
