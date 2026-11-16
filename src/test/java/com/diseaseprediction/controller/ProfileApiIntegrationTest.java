package com.diseaseprediction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.domain.Doctor;
import com.diseaseprediction.domain.Patient;
import com.diseaseprediction.domain.User;
import com.diseaseprediction.repository.DoctorRepository;
import com.diseaseprediction.repository.UserRepository;
import com.diseaseprediction.security.AuthenticatedApiTest;
import com.diseaseprediction.security.TestTokens;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * MDD Iteration 6, Step 5: first-login onboarding, consent, and the consent rule on assessments.
 * Runs through the real security chain against a real PostgreSQL.
 */
class ProfileApiIntegrationTest extends AuthenticatedApiTest {

    private static final String ONBOARDING =
            """
            {"fullName": "Ana Pop", "dateOfBirth": "1990-04-12", "consentGranted": true}
            """;

    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void firstLoginHasNoProfile() throws Exception {
        perform(get("/api/me"), TestTokens.token().build())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("profile-not-found"));
    }

    @Test
    void onboardingCreatesAPatientWithConsentAndTheEmailFromTheToken() throws Exception {
        TestTokens session = TestTokens.token().email("ana@example.com", true);
        String token = session.build();

        perform(post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"))
                .andExpect(jsonPath("$.email").value("ana@example.com"))
                .andExpect(jsonPath("$.fullName").value("Ana Pop"))
                .andExpect(jsonPath("$.dateOfBirth").value("1990-04-12"))
                .andExpect(jsonPath("$.consentGranted").value(true));

        perform(get("/api/me"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ana Pop"));

        User stored = userRepository.findByIdentityId(session.subjectValue()).orElseThrow();
        assertThat(stored).isInstanceOf(Patient.class);
        Integer consentRows =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM consents WHERE patient_id = ? AND granted",
                        Integer.class,
                        stored.getId());
        assertThat(consentRows).isEqualTo(1);
    }

    @Test
    void onboardingTwiceIsAConflict() throws Exception {
        String token = TestTokens.token().build();
        onboard(token);

        perform(post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("profile-exists"));
    }

    @Test
    void onboardingWithoutConsentIsRefused() throws Exception {
        perform(
                        post("/api/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\": \"Ana Pop\", \"consentGranted\": false}"),
                        TestTokens.token().build())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("consent-required"));
    }

    @Disabled(
            "TEMPORARY: email verification is off for the demo without a mail system (see"
                    + " ProfileService.createPatientProfile). Re-enable before production signup.")
    @Test
    void onboardingNeedsAVerifiedEmail() throws Exception {
        perform(
                        post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING),
                        TestTokens.token().email("ana@example.com", false).build())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("email-not-verified"));

        perform(
                        post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING),
                        TestTokens.token().withoutEmail().build())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("email-not-verified"));
    }

    /** TEMPORARY demo behaviour: replace with onboardingNeedsAVerifiedEmail when it is restored. */
    @Test
    void onboardingAcceptsAnUnverifiedEmailButStillNeedsOne() throws Exception {
        perform(
                        post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING),
                        TestTokens.token().email("ana@example.com", false).build())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ana@example.com"));

        perform(
                        post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING),
                        TestTokens.token().withoutEmail().build())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("email-not-verified"));
    }

    @Test
    void onboardingRejectsABlankNameAndAFutureBirthDate() throws Exception {
        String token = TestTokens.token().build();
        perform(
                        post("/api/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\": \"   \", \"consentGranted\": true}"),
                        token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-profile"));
        perform(
                        post("/api/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fullName\": \"Ana\", \"dateOfBirth\": \"2999-01-01\","
                                                + " \"consentGranted\": true}"),
                        token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-profile"));
    }

    @Test
    void theRoleCannotBeChosenByTheClient() throws Exception {
        perform(
                        post("/api/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fullName\": \"Ana\", \"consentGranted\": true,"
                                                + " \"role\": \"ADMIN\"}"),
                        TestTokens.token().build())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("PATIENT"));
    }

    @Test
    void eachUserOnlySeesTheirOwnProfile() throws Exception {
        String ana = TestTokens.token().email("ana@example.com", true).build();
        String ion = TestTokens.token().email("ion@example.com", true).build();
        onboard(ana);
        perform(
                        post("/api/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fullName\": \"Ion Popescu\", \"consentGranted\": true}"),
                        ion)
                .andExpect(status().isCreated());

        perform(get("/api/me"), ana).andExpect(jsonPath("$.email").value("ana@example.com"));
        perform(get("/api/me"), ion)
                .andExpect(jsonPath("$.email").value("ion@example.com"))
                .andExpect(jsonPath("$.fullName").value("Ion Popescu"));
    }

    @Test
    void aChangedVerifiedEmailInHankoIsCopiedOnTheNextProfileRead() throws Exception {
        TestTokens session = TestTokens.token().email("old@example.com", true);
        onboard(session.build());

        perform(get("/api/me"), session.email("new@example.com", true).build())
                .andExpect(jsonPath("$.email").value("new@example.com"));
        assertThat(userRepository.findByIdentityId(session.subjectValue()).orElseThrow().getEmail())
                .isEqualTo("new@example.com");
    }

    @Test
    void consentCanBeRevokedAndGrantedAgain() throws Exception {
        String token = TestTokens.token().build();
        onboard(token);

        perform(consent(false), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentGranted").value(false));
        perform(consent(true), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consentGranted").value(true));
    }

    @Test
    void consentNeedsAPatientProfile() throws Exception {
        perform(consent(true), TestTokens.token().build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("profile-required"));
    }

    @Test
    void patientWithConsentCanSubmitAnAssessment() throws Exception {
        when(predictionClient.predict(any(), any()))
                .thenReturn(
                        new PredictionResult()
                                .resultId(UUID.randomUUID())
                                .generatedAt(OffsetDateTime.now())
                                .disclaimer("This is not a medical diagnosis.")
                                .predictions(List.of()));
        String token = TestTokens.token().build();
        onboard(token);

        perform(assessment(), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyDetected").value(false));
    }

    @Test
    void assessmentWithoutProfileIsRefused() throws Exception {
        perform(assessment(), TestTokens.token().build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("profile-required"));
    }

    @Test
    void assessmentAfterRevokingConsentIsRefused() throws Exception {
        String token = TestTokens.token().build();
        onboard(token);
        perform(consent(false), token).andExpect(status().isCreated());

        perform(assessment(), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("consent-required"));
    }

    @Test
    void doctorsCannotSubmitPatientAssessments() throws Exception {
        TestTokens session = TestTokens.token();
        transactionTemplate.executeWithoutResult(
                status ->
                        doctorRepository.save(
                                new Doctor(
                                        session.subjectValue(),
                                        "dr@example.com",
                                        "Dr. Ionescu",
                                        "Cardiology",
                                        "LIC-" + UUID.randomUUID())));

        perform(assessment(), session.build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
        perform(get("/api/me"), session.build())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.consentGranted").value(false));
    }

    private void onboard(String token) throws Exception {
        perform(post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING), token)
                .andExpect(status().isCreated());
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            consent(boolean granted) {
        return post("/api/me/consents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"granted\": " + granted + "}");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
            assessment() {
        return post("/api/assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"freeText\": \"I have a bad cough and a fever\"}");
    }

    private ResultActions perform(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String token)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }
}
