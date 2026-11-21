package com.diseaseprediction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.FollowUp;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.RedFlag;
import com.diseaseprediction.api.model.Urgency;
import com.diseaseprediction.domain.Doctor;
import com.diseaseprediction.domain.StoredAssessment;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.repository.DoctorRepository;
import com.diseaseprediction.repository.UserRepository;
import com.diseaseprediction.security.AuthenticatedApiTest;
import com.diseaseprediction.security.TestTokens;
import com.diseaseprediction.service.assessment.AssessmentHistoryService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MDD Iteration 7, Step 5: assessments are stored encrypted with their owner, the follow-up loop
 * updates the stored assessment from server-side state, and only the owner can read it. Runs
 * through the real security chain, the access guard aspects and PostgreSQL.
 */
class AssessmentPersistenceIntegrationTest extends AuthenticatedApiTest {

    private static final String ONBOARDING =
            """
            {"fullName": "Ana Pop", "dateOfBirth": "1990-04-12", "consentGranted": true}
            """;
    private static final String FREE_TEXT = "I have a dry cough and a sore throat";

    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private AssessmentHistoryService history;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void anAssessmentIsStoredEncryptedWithADraftReport() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(finalResult("Common Cold"));
        TestTokens session = TestTokens.token();
        String token = onboard(session);

        UUID id = submit(token);

        Map<String, Object> row =
                jdbcTemplate.queryForMap("SELECT * FROM assessments WHERE id = ?", id);
        assertThat(row.get("patient_id")).isEqualTo(patientId(session));
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(row.get("follow_up_rounds")).isEqualTo(0);
        for (String column : List.of("input", "follow_up_state", "result")) {
            assertThat((String) row.get(column)).startsWith("v1:");
        }
        assertThat(row.toString())
                .doesNotContain("cough")
                .doesNotContain("Common Cold")
                .doesNotContain("sore throat");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status FROM reports WHERE assessment_id = ?",
                                String.class,
                                id))
                .isEqualTo("DRAFT");
    }

    @Test
    void thePatientListsAndOpensTheirAssessments() throws Exception {
        when(predictionClient.predict(any(), any()))
                .thenReturn(finalResult("Common Cold"))
                .thenReturn(finalResult("Influenza"));
        String token = onboard(TestTokens.token());
        UUID first = submit(token);
        UUID second = submit(token);

        perform(get("/api/assessments"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second.toString()))
                .andExpect(jsonPath("$[0].topDisease").value("Influenza"))
                .andExpect(jsonPath("$[0].topProbability").value(0.81))
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].reportStatus").value("DRAFT"))
                .andExpect(jsonPath("$[1].id").value(first.toString()));

        perform(get("/api/assessments/" + first), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(first.toString()))
                .andExpect(jsonPath("$.freeText").value(FREE_TEXT))
                .andExpect(jsonPath("$.confirmedSymptoms[0]").value("cough"))
                .andExpect(jsonPath("$.result.predictions[0].diseaseName").value("Common Cold"))
                .andExpect(jsonPath("$.result.disclaimer").value("Not a medical diagnosis."))
                .andExpect(jsonPath("$.report.status").value("DRAFT"));
    }

    @Test
    void theFollowUpLoopUpdatesTheSameAssessmentFromTheStoredState() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(needsFollowUp("fever"));
        when(predictionClient.predictFollowUp(anyList(), anyList(), anyInt()))
                .thenReturn(finalResult("Influenza"));
        String token = onboard(TestTokens.token());
        UUID id = submit(token);
        assertThat(storedStatus(id)).isEqualTo("IN_PROGRESS");

        // The client also sends a tampered symptom list: the stored state wins.
        perform(
                        post("/api/assessments/followup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"assessmentId": "%s",
                                         "confirmedSymptoms": ["chest_pain"], "roundsUsed": 9,
                                         "answer": {"symptom": "fever", "present": true}}
                                        """
                                                .formatted(id)),
                        token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value(id.toString()))
                .andExpect(jsonPath("$.result.predictions[0].diseaseName").value("Influenza"));

        verify(predictionClient).predictFollowUp(List.of("cough", "fever"), List.of(), 1);
        assertThat(storedStatus(id)).isEqualTo("COMPLETED");
        perform(get("/api/assessments/" + id), token)
                .andExpect(jsonPath("$.followUpRounds").value(1))
                .andExpect(jsonPath("$.confirmedSymptoms.length()").value(2))
                .andExpect(jsonPath("$.result.predictions[0].diseaseName").value("Influenza"));

        perform(followUp(id, "headache", false), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("assessment-completed"));
    }

    @Test
    void aNoAnswerIsKeptAsDeniedForTheNextQuestion() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(needsFollowUp("fever"));
        when(predictionClient.predictFollowUp(anyList(), anyList(), anyInt()))
                .thenReturn(needsFollowUp("headache"));
        String token = onboard(TestTokens.token());
        UUID id = submit(token);

        perform(followUp(id, "fever", false), token).andExpect(status().isOk());
        perform(followUp(id, "headache", false), token).andExpect(status().isOk());

        verify(predictionClient).predictFollowUp(List.of("cough"), List.of("fever"), 1);
        verify(predictionClient).predictFollowUp(List.of("cough"), List.of("fever", "headache"), 2);
        assertThat(storedStatus(id)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void anEmergencyEndsTheLoop() throws Exception {
        PredictionResult emergency = needsFollowUp("fever").highestUrgency(Urgency.EMERGENCY);
        emergency.setRedFlags(
                List.of(
                        new RedFlag()
                                .name("possible_cardiac_event")
                                .urgency(Urgency.EMERGENCY)
                                .message("Call 112 now.")
                                .matchedSymptoms(List.of("chest_pain"))));
        when(predictionClient.predict(any(), any())).thenReturn(emergency);
        String token = onboard(TestTokens.token());

        UUID id = submit(token);

        assertThat(storedStatus(id)).isEqualTo("COMPLETED");
        perform(get("/api/assessments"), token)
                .andExpect(jsonPath("$[0].highestUrgency").value("EMERGENCY"));
    }

    @Test
    void anotherPatientsAssessmentAndAnUnknownIdLookTheSame() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(needsFollowUp("fever"));
        String ana = onboard(TestTokens.token());
        TestTokens ionSession = TestTokens.token();
        String ion = onboard(ionSession);
        UUID anasAssessment = submit(ana);
        UUID unknown = UUID.randomUUID();

        String foreignBody =
                perform(get("/api/assessments/" + anasAssessment), ion)
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.code").value("assessment-not-found"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String unknownBody =
                perform(get("/api/assessments/" + unknown), ion)
                        .andExpect(status().isNotFound())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        // Same problem, same text: only the echoed request path differs.
        assertThat(foreignBody.replace(anasAssessment.toString(), "ID"))
                .isEqualTo(unknownBody.replace(unknown.toString(), "ID"));

        perform(followUp(anasAssessment, "fever", true), ion)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("assessment-not-found"));
        perform(get("/api/assessments"), ion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID ionId = patientId(ionSession);
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT action, resource_id, outcome, reason_code"
                                        + " FROM access_audit_events WHERE actor_user_id = ?"
                                        + " AND outcome = 'DENIED' ORDER BY occurred_at",
                                ionId))
                .extracting(
                        r ->
                                r.get("action")
                                        + " "
                                        + r.get("resource_id")
                                        + " "
                                        + r.get("reason_code"))
                .containsExactly(
                        "READ " + anasAssessment + " not-owner",
                        "READ " + unknown + " unknown-resource",
                        "UPDATE " + anasAssessment + " not-owner");
    }

    @Test
    void auditRowsNameTheAssessmentThatWasCreatedAndRead() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(finalResult("Common Cold"));
        TestTokens session = TestTokens.token();
        String token = onboard(session);
        UUID id = submit(token);
        perform(get("/api/assessments/" + id), token).andExpect(status().isOk());

        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT action, resource_id FROM access_audit_events"
                                        + " WHERE actor_user_id = ? AND resource_type = 'ASSESSMENT'"
                                        + " ORDER BY occurred_at",
                                patientId(session)))
                .extracting(r -> r.get("action") + " " + r.get("resource_id"))
                .containsExactly("CREATE " + id, "READ " + id);
    }

    @Test
    void withdrawingConsentStopsNewAnswersButNotReadingYourOwnData() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(needsFollowUp("fever"));
        String token = onboard(TestTokens.token());
        UUID id = submit(token);
        perform(
                        post("/api/me/consents")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"granted\": false}"),
                        token)
                .andExpect(status().isCreated());

        perform(followUp(id, "fever", true), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("consent-required"));
        perform(get("/api/assessments"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        perform(get("/api/assessments/" + id), token).andExpect(status().isOk());
    }

    @Test
    void readingAssessmentsNeedsAPatientWithAVerifiedEmail() throws Exception {
        perform(get("/api/assessments"), TestTokens.token().build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("profile-required"));

        TestTokens doctorSession = TestTokens.token();
        transactionTemplate.executeWithoutResult(
                status ->
                        doctorRepository.save(
                                new Doctor(
                                        doctorSession.subjectValue(),
                                        "dr@example.com",
                                        "Dr. Ionescu",
                                        "Cardiology",
                                        "LIC-" + UUID.randomUUID())));
        perform(get("/api/assessments"), doctorSession.build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        TestTokens session = TestTokens.token();
        onboard(session);
        perform(get("/api/assessments"), session.email("patient@example.com", false).build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("email-not-verified"));
    }

    @Test
    void aFollowUpForAStoredAssessmentNeedsAnAnswer() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(needsFollowUp("fever"));
        String token = onboard(TestTokens.token());
        UUID id = submit(token);

        perform(
                        post("/api/assessments/followup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"assessmentId\": \"" + id + "\"}"),
                        token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("invalid-followup"));
    }

    @Test
    void olderClientsWithoutAnIdStillWorkAndTheirResultIsStored() throws Exception {
        when(predictionClient.predictFollowUp(anyList(), anyList(), anyInt()))
                .thenReturn(finalResult("Influenza"));
        TestTokens session = TestTokens.token();
        String token = onboard(session);

        perform(
                        post("/api/assessments/followup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"confirmedSymptoms": ["cough"], "roundsUsed": 2,
                                         "answer": {"symptom": "fever", "present": true}}
                                        """),
                        token)
                .andExpect(status().isOk());

        verify(predictionClient).predictFollowUp(List.of("cough", "fever"), List.of(), 3);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT follow_up_rounds FROM assessments WHERE patient_id = ?",
                                Integer.class,
                                patientId(session)))
                .isEqualTo(3);
    }

    @Test
    void aStaleAnswerIsAConflict() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(needsFollowUp("fever"));
        String token = onboard(TestTokens.token());
        UUID id = submit(token);
        StoredAssessment stored = history.load(id);

        history.recordFollowUp(id, stored.getVersion(), "{}", null, false);

        assertThatThrownBy(() -> history.recordFollowUp(id, stored.getVersion(), "{}", null, false))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        problem -> assertThat(problem.getCode()).isEqualTo("assessment-conflict"));
    }

    @Test
    void deletingTheAccountDeletesItsAssessmentsAndReports() throws Exception {
        when(predictionClient.predict(any(), any())).thenReturn(finalResult("Common Cold"));
        TestTokens session = TestTokens.token();
        String token = onboard(session);
        UUID id = submit(token);

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", patientId(session));

        assertThat(count("SELECT count(*) FROM assessments WHERE id = ?", id)).isZero();
        assertThat(count("SELECT count(*) FROM reports WHERE assessment_id = ?", id)).isZero();
    }

    private String onboard(TestTokens session) throws Exception {
        String token = session.build();
        perform(post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING), token)
                .andExpect(status().isCreated());
        return token;
    }

    private UUID submit(String token) throws Exception {
        MvcResult result =
                perform(
                                post("/api/assessments")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"freeText\": \"" + FREE_TEXT + "\"}"),
                                token)
                        .andExpect(status().isOk())
                        .andReturn();
        return UUID.fromString(
                json.readTree(result.getResponse().getContentAsString())
                        .get("assessmentId")
                        .asString());
    }

    private UUID patientId(TestTokens session) {
        return userRepository.findByIdentityId(session.subjectValue()).orElseThrow().getId();
    }

    private String storedStatus(UUID id) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM assessments WHERE id = ?", String.class, id);
    }

    private long count(String sql, UUID id) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, id);
        return count == null ? 0 : count;
    }

    private static MockHttpServletRequestBuilder followUp(
            UUID id, String symptom, boolean present) {
        return post("/api/assessments/followup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                        """
                        {"assessmentId": "%s", "answer": {"symptom": "%s", "present": %s}}
                        """
                                .formatted(id, symptom, present));
    }

    private static PredictionResult finalResult(String disease) {
        return result(disease, false, null);
    }

    private static PredictionResult needsFollowUp(String symptom) {
        return result(
                "Common Cold",
                true,
                new FollowUp()
                        .symptom(symptom)
                        .question("Do you have " + symptom + "?")
                        .topDisease("Common Cold")
                        .confidence(0.4));
    }

    private static PredictionResult result(String disease, boolean followUp, FollowUp question) {
        return new PredictionResult()
                .resultId(UUID.randomUUID())
                .generatedAt(OffsetDateTime.parse("2026-11-20T10:00:00Z"))
                .disclaimer("Not a medical diagnosis.")
                .predictions(
                        List.of(
                                new DiseasePrediction()
                                        .rank(1)
                                        .probability(0.81)
                                        .diseaseName(disease)
                                        .precautions(List.of("Rest"))))
                .activeSymptoms(List.of("cough"))
                .needsFollowUp(followUp)
                .followUp(question)
                .redFlags(List.of());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }
}
