package com.diseaseprediction.controller;

import com.diseaseprediction.api.AssessmentsApi;
import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.api.model.AssessmentResponse;
import com.diseaseprediction.service.assessment.AssessmentApiService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Web entry point for patient assessments.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentController} + {@code POST /api/assessments}.
 * Anchored to UC1 (Patient Triage) and FR-P1 (free text / checklist input). The contract is now
 * owned by the OpenAPI spec ({@code src/main/resources/openapi/assessment-api.yaml}); this
 * controller implements the generated {@link AssessmentsApi} interface, enforces the symptom-signal
 * contract, and delegates orchestration to {@link AssessmentApiService}.
 */
@RestController
public class AssessmentController implements AssessmentsApi {

    private final AssessmentApiService assessmentApiService;

    public AssessmentController(AssessmentApiService assessmentApiService) {
        this.assessmentApiService = assessmentApiService;
    }

    /**
     * Submit an assessment and receive a prediction result.
     *
     * @param request the assessment payload (free text and/or symptom entries)
     * @return 200 with an {@link AssessmentResponse}; 400 if the payload carries no symptom signal
     */
    @Override
    public ResponseEntity<AssessmentResponse> submitAssessment(AssessmentRequest request) {
        if (request == null || !hasAnySymptomSignal(request)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assessment must include free text or at least one symptom entry.");
        }
        return ResponseEntity.ok(assessmentApiService.runAssessment(request));
    }

    /**
     * A request carries a symptom signal when it has either non-blank free text or at least one
     * symptom entry. Relocated here from the hand-written DTO so the generated API DTOs stay free
     * of hand-authored behaviour (the convenience check lives in the web layer, producing a 400 for
     * empty payloads).
     */
    private static boolean hasAnySymptomSignal(AssessmentRequest request) {
        boolean hasText = request.getFreeText() != null && !request.getFreeText().isBlank();
        boolean hasEntries =
                request.getSymptomEntries() != null && !request.getSymptomEntries().isEmpty();
        return hasText || hasEntries;
    }
}
