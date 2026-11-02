package com.diseaseprediction.controller;

import com.diseaseprediction.api.AssessmentsApi;
import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.api.model.AssessmentResponse;
import com.diseaseprediction.service.assessment.AssessmentApiService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web entry point for patient assessments.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentController} + {@code POST /api/assessments}.
 * Anchored to UC1 (Patient Triage) and FR-P1 (free text / checklist input). The contract is now
 * owned by the OpenAPI spec ({@code src/main/resources/openapi/assessment-api.yaml}); this
 * controller implements the generated {@link AssessmentsApi} interface and is a thin pass-through
 * that delegates all orchestration and validation to {@link AssessmentApiService}.
 */
@RestController
public class AssessmentController implements AssessmentsApi {

    private final AssessmentApiService assessmentApiService;

    public AssessmentController(AssessmentApiService assessmentApiService) {
        this.assessmentApiService = assessmentApiService;
    }

    /**
     * Submit an assessment and receive a prediction result. All validation and orchestration live
     * in the service; a request with no symptom signal surfaces as HTTP 400 via the service's
     * {@code InvalidAssessmentRequestException}.
     *
     * @param request the assessment payload (free text and/or symptom entries)
     * @return 200 with an {@link AssessmentResponse}
     */
    @Override
    public ResponseEntity<AssessmentResponse> submitAssessment(AssessmentRequest request) {
        return ResponseEntity.ok(assessmentApiService.runAssessment(request));
    }
}
