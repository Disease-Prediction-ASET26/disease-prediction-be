package com.diseaseprediction.service.assessment;

import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.api.model.AssessmentResponse;
import com.diseaseprediction.api.model.FollowupAnswer;
import com.diseaseprediction.api.model.FollowupRequest;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.Urgency;
import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;
import com.diseaseprediction.security.access.AuditedAccess;
import com.diseaseprediction.security.access.RequiresActiveConsent;
import com.diseaseprediction.service.prediction.PredictionClient;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates an assessment submitted over HTTP.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentService.runAssessment()}. The backend does
 * not predict; it orchestrates: it delegates prediction to the {@link PredictionClient} across the
 * SOA boundary and relays the result.
 *
 * <p>Red-flag urgency: the prediction service is the single source of truth. Its result carries
 * {@code redFlags} and {@code highestUrgency}; {@code emergencyDetected} is derived from them
 * ({@code highestUrgency == EMERGENCY}) and kept for older clients. The backend no longer keeps its
 * own keyword list.
 *
 * <p>This service is the seam between the OpenAPI-generated API DTOs ({@link AssessmentRequest} /
 * {@link AssessmentResponse} / {@link PredictionResult}) and the domain model. The
 * DTO&lt;-&gt;domain boundary deliberately lives here so MapStruct mappers can later be slotted in
 * without touching the generated DTOs or the controller.
 *
 * <p>Named {@code AssessmentApiService} to coexist with the pre-existing domain-coupled {@code
 * AssessmentService}; this class is the web-layer orchestrator introduced by Iteration 2.
 *
 * <p>Access rules are declared, not coded: {@link RequiresActiveConsent} lets only a consenting
 * patient in, and {@link AuditedAccess} records every call (Lab 9, Personal Data Access Guard).
 */
@Service
public class AssessmentApiService {

    private final PredictionClient predictionClient;

    public AssessmentApiService(PredictionClient predictionClient) {
        this.predictionClient = predictionClient;
    }

    /**
     * Run an assessment: delegate to the prediction service and relay its urgency.
     *
     * <p>The red-flag check now happens in the prediction service, so an emergency is reported on
     * the same call as the prediction. If the service cannot be reached the request fails (503) and
     * no urgency can be computed; the client's error message keeps the 112 advice.
     *
     * @param request the assessment request
     * @return the assessment response carrying the emergency flag and prediction result
     *     <p>MDD Iteration 6: only a signed-in patient with an active consent may submit (UC1 step
     *     1), enforced by {@link RequiresActiveConsent}. The patient comes from the session token;
     *     {@code request.patientId} is ignored.
     * @throws InvalidAssessmentRequestException if the request carries no symptom signal
     * @throws com.diseaseprediction.exception.ApiProblemException 403 without a patient profile or
     *     an active consent
     */
    @AuditedAccess(action = AccessAction.CREATE, resource = ResourceType.ASSESSMENT)
    @RequiresActiveConsent
    public AssessmentResponse runAssessment(AssessmentRequest request) {
        if (request == null || !hasAnySymptomSignal(request)) {
            throw new InvalidAssessmentRequestException(
                    "Assessment must include free text or at least one symptom entry with text.");
        }

        PredictionResult result =
                predictionClient.predict(request.getFreeText(), request.getSymptomEntries());

        return new AssessmentResponse()
                .assessmentId(UUID.randomUUID())
                .emergencyDetected(isEmergency(result))
                .result(result);
    }

    /**
     * Continue an adaptive assessment after a follow-up answer (FR-P3).
     *
     * <p>Resolves the yes/no answer into the running confirmed/denied symptom sets, then delegates
     * the re-ranking to the prediction service. The response may carry another follow-up question;
     * the loop ends when its result has {@code needsFollowUp == false}. Stateless on the server:
     * the client carries the accumulated symptom sets each turn.
     *
     * <p>MDD Iteration 6: gated exactly like {@link #runAssessment} — only a signed-in patient with
     * an active consent may continue.
     *
     * @param request the follow-up payload (confirmed/denied symptoms so far + the latest answer)
     * @return the re-ranked assessment response
     * @throws InvalidAssessmentRequestException if no confirmed symptoms remain after the answer
     * @throws com.diseaseprediction.exception.ApiProblemException 403 without a patient profile or
     *     an active consent
     */
    @AuditedAccess(action = AccessAction.UPDATE, resource = ResourceType.ASSESSMENT)
    @RequiresActiveConsent
    public AssessmentResponse continueAssessment(FollowupRequest request) {
        if (request == null) {
            throw new InvalidAssessmentRequestException("A follow-up request is required.");
        }

        List<String> confirmed = new ArrayList<>(nullToEmpty(request.getConfirmedSymptoms()));
        List<String> denied = new ArrayList<>(nullToEmpty(request.getDeniedSymptoms()));
        int roundsUsed = request.getRoundsUsed() == null ? 0 : request.getRoundsUsed();

        FollowupAnswer answer = request.getAnswer();
        if (answer != null && answer.getSymptom() != null && !answer.getSymptom().isBlank()) {
            roundsUsed++;
            if (Boolean.TRUE.equals(answer.getPresent())) {
                confirmed.add(answer.getSymptom());
            } else {
                denied.add(answer.getSymptom());
            }
        }

        if (confirmed.isEmpty()) {
            throw new InvalidAssessmentRequestException(
                    "A follow-up must carry at least one confirmed symptom.");
        }

        PredictionResult result = predictionClient.predictFollowUp(confirmed, denied, roundsUsed);

        return new AssessmentResponse()
                .assessmentId(UUID.randomUUID())
                .emergencyDetected(isEmergency(result))
                .result(result);
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }

    /**
     * A request carries a symptom signal when it has either non-blank free text or at least one
     * non-blank symptom entry. The validation lives in the service so controllers remain thin.
     */
    private static boolean hasAnySymptomSignal(AssessmentRequest request) {
        boolean hasText = request.getFreeText() != null && !request.getFreeText().isBlank();
        boolean hasEntries =
                request.getSymptomEntries() != null
                        && request.getSymptomEntries().stream()
                                .anyMatch(
                                        entry ->
                                                entry != null
                                                        && entry.getRawText() != null
                                                        && !entry.getRawText().isBlank());
        return hasText || hasEntries;
    }

    /** {@code emergencyDetected} is true exactly when the relayed urgency is EMERGENCY. */
    private static boolean isEmergency(PredictionResult result) {
        return result != null && result.getHighestUrgency() == Urgency.EMERGENCY;
    }
}
