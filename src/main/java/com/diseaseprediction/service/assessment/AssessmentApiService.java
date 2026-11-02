package com.diseaseprediction.service.assessment;

import com.diseaseprediction.controller.dto.AssessmentRequest;
import com.diseaseprediction.controller.dto.AssessmentResponse;
import com.diseaseprediction.controller.dto.PredictionResultDto;
import com.diseaseprediction.service.prediction.PredictionClient;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates an assessment submitted over HTTP.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentService.runAssessment()}. The backend does
 * not predict; it orchestrates: it runs a lightweight emergency (red-flag) check and then delegates
 * prediction to the {@link PredictionClient} across the SOA boundary.
 *
 * <p>Named {@code AssessmentApiService} to coexist with the pre-existing domain-coupled {@code
 * AssessmentService}; this class is the web-layer orchestrator introduced by Iteration 2.
 */
@Service
public class AssessmentApiService {

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
     * @param request the validated assessment request
     * @return the assessment response carrying the emergency flag and prediction result
     */
    public AssessmentResponse runAssessment(AssessmentRequest request) {
        boolean emergencyDetected = detectEmergency(request);

        PredictionResultDto result =
                predictionClient.predict(request.freeText(), request.symptomEntries());

        return new AssessmentResponse(UUID.randomUUID(), emergencyDetected, result);
    }

    /**
     * Minimal red-flag detection on the free text. A placeholder for the domain's {@code
     * RedFlagRule} chain; kept here so the web slice is self-contained and testable.
     */
    private boolean detectEmergency(AssessmentRequest request) {
        String text = request.freeText();
        if (text == null) {
            return false;
        }
        String lowered = text.toLowerCase();
        return lowered.contains("chest pain")
                || lowered.contains("difficulty breathing")
                || lowered.contains("shortness of breath");
    }
}
