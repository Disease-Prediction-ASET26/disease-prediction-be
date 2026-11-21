package com.diseaseprediction.service.assessment;

import com.diseaseprediction.api.model.AssessmentDetail;
import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.api.model.AssessmentResponse;
import com.diseaseprediction.api.model.AssessmentSummary;
import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.FollowupAnswer;
import com.diseaseprediction.api.model.FollowupRequest;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.ReportSummary;
import com.diseaseprediction.api.model.Urgency;
import com.diseaseprediction.domain.Patient;
import com.diseaseprediction.domain.StoredAssessment;
import com.diseaseprediction.domain.StoredReport;
import com.diseaseprediction.domain.enums.AccessAction;
import com.diseaseprediction.domain.enums.ResourceType;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;
import com.diseaseprediction.security.access.AuditedAccess;
import com.diseaseprediction.security.access.OwnedResource;
import com.diseaseprediction.security.access.RequiresActiveConsent;
import com.diseaseprediction.service.assessment.AssessmentSnapshots.AssessmentInput;
import com.diseaseprediction.service.assessment.AssessmentSnapshots.FollowUpState;
import com.diseaseprediction.service.prediction.PredictionClient;
import com.diseaseprediction.service.profile.ProfileService;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
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
 * ({@code highestUrgency == EMERGENCY}) and kept for older clients.
 *
 * <p>MDD Iteration 7: every assessment is stored with its owner ({@link AssessmentHistoryService}),
 * the follow-up loop updates the stored assessment, and the patient can list and open their
 * assessments. The prediction call happens outside any database transaction.
 *
 * <p>This service is the seam between the OpenAPI-generated API DTOs and the domain model.
 *
 * <p>Access rules are declared, not coded: {@link RequiresActiveConsent} lets only a consenting
 * patient in, {@link OwnedResource} only the owner of the assessment, and {@link AuditedAccess}
 * records every call (Lab 9, Personal Data Access Guard).
 */
@Service
public class AssessmentApiService {

    private final PredictionClient predictionClient;
    private final ProfileService profileService;
    private final AssessmentHistoryService history;
    private final AssessmentSnapshots snapshots;

    public AssessmentApiService(
            PredictionClient predictionClient,
            ProfileService profileService,
            AssessmentHistoryService history,
            AssessmentSnapshots snapshots) {
        this.predictionClient = predictionClient;
        this.profileService = profileService;
        this.history = history;
        this.snapshots = snapshots;
    }

    /**
     * Run and store an assessment: delegate to the prediction service, store the input and the
     * result for the signed-in patient, and relay the result with the stored id.
     *
     * <p>If the prediction service cannot be reached the request fails (503) and nothing is stored;
     * the client's error message keeps the 112 advice.
     *
     * @throws InvalidAssessmentRequestException if the request carries no symptom signal
     * @throws ApiProblemException 403 without a patient profile or an active consent
     */
    @AuditedAccess(
            action = AccessAction.CREATE,
            resource = ResourceType.ASSESSMENT,
            resourceId = "#result?.assessmentId")
    @RequiresActiveConsent
    public AssessmentResponse runAssessment(AssessmentRequest request) {
        if (request == null || !hasAnySymptomSignal(request)) {
            throw new InvalidAssessmentRequestException(
                    "Assessment must include free text or at least one symptom entry with text.");
        }
        PredictionResult result =
                predictionClient.predict(request.getFreeText(), request.getSymptomEntries());
        StoredAssessment stored =
                history.create(
                        currentPatient().getId(),
                        snapshots.write(
                                new AssessmentInput(
                                        request.getFreeText(), request.getSymptomEntries())),
                        snapshots.write(FollowUpState.start(result)),
                        0,
                        snapshots.write(result),
                        isFinal(result));
        return response(stored.getId(), result);
    }

    /**
     * Continue an adaptive assessment after a follow-up answer (FR-P3).
     *
     * <p>With {@code assessmentId} (MDD Iteration 7) the server uses the stored confirmed and
     * denied symptoms and rounds, applies the answer, re-ranks, and updates the same assessment.
     * The loop ends when the result has {@code needsFollowUp == false} or an emergency.
     *
     * <p>Without {@code assessmentId} (older clients) the stateless path runs: the client carries
     * the symptom sets, and the result is stored as a new assessment.
     *
     * @throws InvalidAssessmentRequestException if no confirmed symptoms remain after the answer
     * @throws ApiProblemException 400 {@code invalid-followup} for an id without an answer, 403
     *     without consent, 404 {@code assessment-not-found} for an unknown or foreign id, 409
     *     {@code assessment-completed} or {@code assessment-conflict}
     */
    @AuditedAccess(
            action = AccessAction.UPDATE,
            resource = ResourceType.ASSESSMENT,
            resourceId = "#request?.assessmentId ?: #result?.assessmentId")
    @RequiresActiveConsent
    @OwnedResource(
            resource = ResourceType.ASSESSMENT,
            id = "#request?.assessmentId",
            optional = true)
    public AssessmentResponse continueAssessment(FollowupRequest request) {
        if (request == null) {
            throw new InvalidAssessmentRequestException("A follow-up request is required.");
        }
        if (request.getAssessmentId() == null) {
            return continueStateless(request);
        }
        FollowupAnswer answer = request.getAnswer();
        if (answer == null
                || answer.getSymptom() == null
                || answer.getSymptom().isBlank()
                || answer.getPresent() == null) {
            throw ApiProblemException.invalidFollowup(
                    "A follow-up for a stored assessment needs an answer.");
        }
        StoredAssessment stored = history.load(request.getAssessmentId());
        if (stored.isCompleted()) {
            throw ApiProblemException.assessmentCompleted();
        }
        FollowUpState state = snapshots.readState(stored.getFollowUpState()).answer(answer);
        if (state.confirmedSymptoms().isEmpty()) {
            throw new InvalidAssessmentRequestException(
                    "A follow-up must carry at least one confirmed symptom.");
        }
        PredictionResult result =
                predictionClient.predictFollowUp(
                        state.confirmedSymptoms(),
                        state.deniedSymptoms(),
                        stored.getFollowUpRounds() + 1);
        history.recordFollowUp(
                stored.getId(),
                stored.getVersion(),
                snapshots.write(state.after(result)),
                snapshots.write(result),
                isFinal(result));
        return response(stored.getId(), result);
    }

    /**
     * The signed-in patient's assessments, newest first (MDD Iteration 7). Needs a verified email
     * but no active consent.
     */
    @AuditedAccess(action = AccessAction.READ, resource = ResourceType.ASSESSMENT)
    public List<AssessmentSummary> listAssessments() {
        Patient patient = profileService.requirePatient();
        return history.listFor(patient.getId()).stream().map(this::summary).toList();
    }

    /**
     * One of the signed-in patient's assessments (MDD Iteration 7). Only the owner gets it.
     *
     * @throws ApiProblemException 404 {@code assessment-not-found} for an unknown or foreign id
     */
    @AuditedAccess(
            action = AccessAction.READ,
            resource = ResourceType.ASSESSMENT,
            resourceId = "#assessmentId")
    @OwnedResource(resource = ResourceType.ASSESSMENT, id = "#assessmentId")
    public AssessmentDetail getAssessment(UUID assessmentId) {
        profileService.requirePatient();
        StoredAssessment stored = history.load(assessmentId);
        AssessmentInput input = snapshots.readInput(stored.getInput());
        FollowUpState state = snapshots.readState(stored.getFollowUpState());
        StoredReport report = stored.getReport();
        return new AssessmentDetail()
                .id(stored.getId())
                .status(apiStatus(stored))
                .createdAt(stored.getCreatedAt())
                .updatedAt(stored.getUpdatedAt())
                .followUpRounds(stored.getFollowUpRounds())
                .freeText(input.freeText())
                .symptomEntries(new ArrayList<>(input.symptomEntries()))
                .confirmedSymptoms(new ArrayList<>(state.confirmedSymptoms()))
                .deniedSymptoms(new ArrayList<>(state.deniedSymptoms()))
                .result(snapshots.readResult(stored.getResult()))
                .report(
                        new ReportSummary()
                                .id(report.getId())
                                .status(apiReportStatus(report))
                                .createdAt(report.getCreatedAt())
                                .sentAt(report.getSentAt()));
    }

    /** The pre-Iteration 7 loop: the client carries the symptom sets; stored as a new record. */
    private AssessmentResponse continueStateless(FollowupRequest request) {
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
        FollowUpState state =
                new FollowUpState(confirmed, denied, answer == null ? List.of() : List.of(answer));
        StoredAssessment stored =
                history.create(
                        currentPatient().getId(),
                        snapshots.write(new AssessmentInput(null, List.of())),
                        snapshots.write(state.after(result)),
                        Math.max(roundsUsed, 0),
                        snapshots.write(result),
                        isFinal(result));
        return response(stored.getId(), result);
    }

    /** The consent guard has already checked that the caller is a consenting patient. */
    private Patient currentPatient() {
        return profileService.requireConsentingPatient();
    }

    private AssessmentSummary summary(StoredAssessment stored) {
        PredictionResult result = snapshots.readResult(stored.getResult());
        DiseasePrediction top = topPrediction(result);
        return new AssessmentSummary()
                .id(stored.getId())
                .status(apiStatus(stored))
                .createdAt(stored.getCreatedAt())
                .updatedAt(stored.getUpdatedAt())
                .followUpRounds(stored.getFollowUpRounds())
                .topDisease(top == null ? null : top.getDiseaseName())
                .topProbability(top == null ? null : top.getProbability())
                .highestUrgency(result == null ? null : result.getHighestUrgency())
                .reportStatus(apiReportStatus(stored.getReport()));
    }

    private static DiseasePrediction topPrediction(PredictionResult result) {
        if (result == null || result.getPredictions() == null) {
            return null;
        }
        return result.getPredictions().stream()
                .filter(p -> p != null)
                .min(
                        Comparator.comparing(
                                DiseasePrediction::getRank,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static com.diseaseprediction.api.model.AssessmentStatus apiStatus(
            StoredAssessment stored) {
        return com.diseaseprediction.api.model.AssessmentStatus.valueOf(stored.getStatus().name());
    }

    private static com.diseaseprediction.api.model.ReportStatus apiReportStatus(
            StoredReport report) {
        return com.diseaseprediction.api.model.ReportStatus.valueOf(report.getStatus().name());
    }

    private static AssessmentResponse response(UUID assessmentId, PredictionResult result) {
        return new AssessmentResponse()
                .assessmentId(assessmentId)
                .emergencyDetected(isEmergency(result))
                .result(result);
    }

    /** No more questions: the service asked none, or an emergency ends the loop. */
    private static boolean isFinal(PredictionResult result) {
        return result == null
                || !Boolean.TRUE.equals(result.getNeedsFollowUp())
                || isEmergency(result);
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
