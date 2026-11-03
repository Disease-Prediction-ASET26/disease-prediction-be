package com.diseaseprediction.service.assessment;

import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.api.model.AssessmentResponse;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;
import com.diseaseprediction.service.prediction.PredictionClient;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Orchestrates an assessment submitted over HTTP.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentService.runAssessment()}. The backend does
 * not predict; it orchestrates: it runs a lightweight emergency (red-flag) check and then delegates
 * prediction to the {@link PredictionClient} across the SOA boundary.
 *
 * <p>This service is the seam between the OpenAPI-generated API DTOs ({@link AssessmentRequest} /
 * {@link AssessmentResponse} / {@link PredictionResult}) and the domain model. The DTO&lt;-&gt;domain
 * boundary deliberately lives here so MapStruct mappers can later be slotted in without touching
 * the generated DTOs or the controller.
 *
 * <p>Named {@code AssessmentApiService} to coexist with the pre-existing domain-coupled {@code
 * AssessmentService}; this class is the web-layer orchestrator introduced by Iteration 2.
 */
@Service
public class AssessmentApiService {

    /** Red-flag phrases, matched in free text and in checklist symptoms (underscores as spaces). */
    private static final List<String> RED_FLAG_PHRASES =
            List.of("chest pain", "difficulty breathing", "shortness of breath", "breathlessness");

    private final PredictionClient predictionClient;

    public AssessmentApiService(PredictionClient predictionClient) {
        this.predictionClient = predictionClient;
    }

    /**
     * Run an assessment: emergency check first, then delegated prediction.
     *
     * <p>Step 6 refinement decision recorded in the MDD doc: the emergency check runs
     * <em>before</em> calling the prediction service so an emergency is surfaced even if prediction
     * fails.
     *
     * @param request the assessment request
     * @return the assessment response carrying the emergency flag and prediction result
     * @throws InvalidAssessmentRequestException if the request carries no symptom signal
     */
    public AssessmentResponse runAssessment(AssessmentRequest request) {
        if (request == null || !hasAnySymptomSignal(request)) {
            throw new InvalidAssessmentRequestException(
                    "Assessment must include free text or at least one symptom entry.");
        }

        boolean emergencyDetected = detectEmergency(request);

        PredictionResult result =
                predictionClient.predict(request.getFreeText(), request.getSymptomEntries());

        return new AssessmentResponse()
                .assessmentId(UUID.randomUUID())
                .emergencyDetected(emergencyDetected)
                .result(result);
    }

    /**
     * A request carries a symptom signal when it has either non-blank free text or at least one
     * symptom entry. The validation lives in the service so controllers remain thin pass-throughs.
     */
    private static boolean hasAnySymptomSignal(AssessmentRequest request) {
        boolean hasText = request.getFreeText() != null && !request.getFreeText().isBlank();
        boolean hasEntries =
                request.getSymptomEntries() != null && !request.getSymptomEntries().isEmpty();
        return hasText || hasEntries;
    }

    /**
     * Minimal red-flag detection on the free text and the checklist symptom entries (UC1 seq.
     * {@code matches(symptomEntries)}). A placeholder for the domain's {@code RedFlagRule} chain;
     * kept here so the web slice is self-contained and testable.
     */
    private boolean detectEmergency(AssessmentRequest request) {
        if (containsRedFlag(request.getFreeText())) {
            return true;
        }
        List<SymptomEntry> entries = request.getSymptomEntries();
        return entries != null
                && entries.stream()
                        .anyMatch(entry -> entry != null && containsRedFlag(entry.getRawText()));
    }

    private static boolean containsRedFlag(String text) {
        if (text == null) {
            return false;
        }
        String normalised = text.replace('_', ' ').toLowerCase(Locale.ROOT);
        return RED_FLAG_PHRASES.stream().anyMatch(normalised::contains);
    }
}
