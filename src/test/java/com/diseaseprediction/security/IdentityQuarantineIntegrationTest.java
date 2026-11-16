package com.diseaseprediction.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

/** Exercises quarantine in the real chain, including signature and session validation. */
@TestPropertySource(
        properties = {
            "app.auth.hanko.quarantine.enabled=true",
            "app.auth.hanko.quarantine.file=src/test/resources/quarantined-subjects.txt"
        })
class IdentityQuarantineIntegrationTest extends AuthenticatedApiTest {
    private static final String BLOCKED = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa";

    @Test
    void quarantinedUserCannotReadCreateOrAssessEvenWithVerifiedEmail() throws Exception {
        String token =
                bearer(TestTokens.token().subject(BLOCKED).email("test@example.com", true).build());
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account-quarantined"));
        mockMvc.perform(
                        post("/api/me")
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account-quarantined"));
        mockMvc.perform(
                        post("/api/assessments")
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("account-quarantined"));
    }

    @Test
    void anotherValidSubjectCanContinue() throws Exception {
        mockMvc.perform(
                        get("/api/me")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        bearer(TestTokens.token().build())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("profile-not-found"));
    }
}
