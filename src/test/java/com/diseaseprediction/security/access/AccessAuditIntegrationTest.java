package com.diseaseprediction.security.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.domain.AccessAuditEvent;
import com.diseaseprediction.domain.Doctor;
import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.AccessOutcome;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.domain.enums.UserRole;
import com.diseaseprediction.repository.AccessAuditEventRepository;
import com.diseaseprediction.repository.DoctorRepository;
import com.diseaseprediction.repository.UserRepository;
import com.diseaseprediction.security.AuthenticatedApiTest;
import com.diseaseprediction.security.TestTokens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Personal Data Access Guard end to end: real security chain, real aspects, real PostgreSQL with
 * the V3 migration. Every guarded call leaves one audit row, denials included, and the table cannot
 * be rewritten.
 */
class AccessAuditIntegrationTest extends AuthenticatedApiTest {

    private static final String ONBOARDING =
            """
            {"fullName": "Ana Pop", "dateOfBirth": "1990-04-12", "consentGranted": true}
            """;

    @Autowired private AccessAuditEventRepository auditEvents;
    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private AuditTrail auditTrail;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void everyGuardedCallOfAPatientIsRecordedInOrder() throws Exception {
        stubPrediction();
        TestTokens session = TestTokens.token().email("ana@example.com", true);
        String token = session.build();

        perform(onboarding(), token).andExpect(status().isCreated());
        perform(get("/api/me"), token).andExpect(status().isOk());
        perform(assessment(), token).andExpect(status().isOk());
        perform(consent(false), token).andExpect(status().isCreated());
        perform(assessment(), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("consent-required"));

        UUID anaId = userRepository.findByIdentityId(session.subjectValue()).orElseThrow().getId();
        assertThat(auditEvents.findByActorUserIdOrderByOccurredAtAsc(anaId))
                .extracting(
                        AccessAuditEvent::getAction,
                        AccessAuditEvent::getResourceType,
                        AccessAuditEvent::getOutcome,
                        AccessAuditEvent::getReasonCode,
                        AccessAuditEvent::getActorRole)
                .containsExactly(
                        tuple(
                                AccessAction.CREATE,
                                ResourceType.PATIENT_PROFILE,
                                AccessOutcome.ALLOWED,
                                null,
                                UserRole.PATIENT),
                        tuple(
                                AccessAction.READ,
                                ResourceType.PATIENT_PROFILE,
                                AccessOutcome.ALLOWED,
                                null,
                                UserRole.PATIENT),
                        tuple(
                                AccessAction.CREATE,
                                ResourceType.ASSESSMENT,
                                AccessOutcome.ALLOWED,
                                null,
                                UserRole.PATIENT),
                        tuple(
                                AccessAction.UPDATE,
                                ResourceType.CONSENT,
                                AccessOutcome.ALLOWED,
                                null,
                                UserRole.PATIENT),
                        tuple(
                                AccessAction.CREATE,
                                ResourceType.ASSESSMENT,
                                AccessOutcome.DENIED,
                                "consent-required",
                                UserRole.PATIENT));
    }

    @Test
    void theFollowUpEndpointIsGuardedByConsentToo() throws Exception {
        TestTokens session = TestTokens.token();
        String token = session.build();
        perform(onboarding(), token).andExpect(status().isCreated());
        perform(consent(false), token).andExpect(status().isCreated());

        perform(followup(), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("consent-required"));

        UUID id = userRepository.findByIdentityId(session.subjectValue()).orElseThrow().getId();
        assertThat(auditEvents.findByActorUserIdOrderByOccurredAtAsc(id))
                .last()
                .extracting(
                        AccessAuditEvent::getAction,
                        AccessAuditEvent::getOutcome,
                        AccessAuditEvent::getReasonCode)
                .containsExactly(AccessAction.UPDATE, AccessOutcome.DENIED, "consent-required");
    }

    /**
     * {@code POST /api/assessments} is refused for non-patients by the security filter chain before
     * any aspect runs; the follow-up endpoint has no role rule there, so the consent guard is what
     * stops a doctor.
     */
    @Test
    void aDoctorContinuingAPatientAssessmentIsRecordedAsDenied() throws Exception {
        TestTokens session = TestTokens.token();
        Doctor doctor =
                transactionTemplate.execute(
                        status ->
                                doctorRepository.save(
                                        new Doctor(
                                                session.subjectValue(),
                                                "dr@example.com",
                                                "Dr. Ionescu",
                                                "Cardiology",
                                                "LIC-" + UUID.randomUUID())));

        perform(followup(), session.build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));

        assertThat(auditEvents.findByActorUserIdOrderByOccurredAtAsc(doctor.getId()))
                .singleElement()
                .extracting(
                        AccessAuditEvent::getActorRole,
                        AccessAuditEvent::getOutcome,
                        AccessAuditEvent::getReasonCode)
                .containsExactly(UserRole.DOCTOR, AccessOutcome.DENIED, "forbidden");
    }

    @Test
    void aCallerWithoutAProfileIsRecordedWithoutAnActor() throws Exception {
        long before = anonymousDenials("profile-required");

        perform(followup(), TestTokens.token().build())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("profile-required"));

        assertThat(anonymousDenials("profile-required")).isEqualTo(before + 1);
    }

    @Test
    void auditRowsHoldNoPersonalData() throws Exception {
        TestTokens session = TestTokens.token().email("maria.secret@example.com", true);
        String token = session.build();
        perform(
                        post("/api/me")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"fullName\": \"Maria Secretescu\", \"consentGranted\":"
                                                + " true}"),
                        token)
                .andExpect(status().isCreated());
        perform(get("/api/me"), token).andExpect(status().isOk());

        UUID id = userRepository.findByIdentityId(session.subjectValue()).orElseThrow().getId();
        List<Map<String, Object>> rows =
                jdbcTemplate.queryForList(
                        "SELECT * FROM access_audit_events WHERE actor_user_id = ?", id);
        assertThat(rows).hasSize(2);
        assertThat(rows.toString())
                .doesNotContain("maria.secret@example.com")
                .doesNotContain("Secretescu")
                .doesNotContain(session.subjectValue());
    }

    @Test
    void aDenialIsKeptWhenTheSurroundingTransactionRollsBack() {
        long before = anonymousDenials("rollback-probe");

        transactionTemplate.executeWithoutResult(
                status -> {
                    auditTrail.record(
                            AccessAction.READ,
                            ResourceType.ASSESSMENT,
                            null,
                            AccessOutcome.DENIED,
                            "rollback-probe");
                    status.setRollbackOnly();
                });

        assertThat(anonymousDenials("rollback-probe")).isEqualTo(before + 1);
    }

    @Test
    void theAuditTableIsAppendOnly() {
        AccessAuditEvent event =
                auditTrail.record(
                        AccessAction.READ,
                        ResourceType.PATIENT_PROFILE,
                        null,
                        AccessOutcome.DENIED,
                        "append-only-probe");

        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "UPDATE access_audit_events SET outcome = 'ALLOWED'"
                                                + " WHERE id = ?",
                                        event.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "DELETE FROM access_audit_events WHERE id = ?",
                                        event.getId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbcTemplate.execute("TRUNCATE access_audit_events"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");

        assertThat(auditEvents.findById(event.getId()))
                .get()
                .extracting(AccessAuditEvent::getOutcome)
                .isEqualTo(AccessOutcome.DENIED);
    }

    private long anonymousDenials(String reasonCode) {
        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM access_audit_events WHERE actor_user_id IS NULL"
                                + " AND outcome = 'DENIED' AND reason_code = ?",
                        Long.class,
                        reasonCode);
        return count == null ? 0 : count;
    }

    private void stubPrediction() {
        when(predictionClient.predict(any(), any()))
                .thenReturn(
                        new PredictionResult()
                                .resultId(UUID.randomUUID())
                                .generatedAt(OffsetDateTime.now())
                                .disclaimer("This is not a medical diagnosis.")
                                .predictions(List.of()));
    }

    private static MockHttpServletRequestBuilder onboarding() {
        return post("/api/me").contentType(MediaType.APPLICATION_JSON).content(ONBOARDING);
    }

    private static MockHttpServletRequestBuilder followup() {
        return post("/api/assessments/followup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmedSymptoms\": [\"cough\"]}");
    }

    private static MockHttpServletRequestBuilder consent(boolean granted) {
        return post("/api/me/consents")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"granted\": " + granted + "}");
    }

    private static MockHttpServletRequestBuilder assessment() {
        return post("/api/assessments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"freeText\": \"I have a bad cough and a fever\"}");
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }
}
