package com.diseaseprediction.controller;

import com.diseaseprediction.controller.dto.AssessmentRequest;
import com.diseaseprediction.controller.dto.AssessmentResponse;
import com.diseaseprediction.service.assessment.AssessmentApiService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Web entry point for patient assessments.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentController} + {@code POST /api/assessments}.
 * Anchored to UC1 (Patient Triage) and FR-P1 (free text / checklist input). The controller is the
 * missing web layer; it receives an {@link AssessmentRequest}, enforces the contract, and returns
 * an {@link AssessmentResponse}.
 */
@RestController
@RequestMapping("/api/assessments")
public class AssessmentController {

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
    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<AssessmentResponse> submit(@RequestBody AssessmentRequest request) {
        if (request == null || !request.hasAnySymptomSignal()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Assessment must include free text or at least one symptom entry.");
        }
        return ResponseEntity.ok(assessmentApiService.runAssessment(request));
    }
}
