package com.diseaseprediction.service.assessment;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.service.prediction.PredictionClient;

import org.junit.jupiter.api.Test;

import java.util.List;

class AssessmentApiServiceTest {

    @Test
    void emergencyAssessmentSkipsPredictionService() {
        PredictionClient predictionClient =
                (freeText, symptomEntries) -> {
                    throw new AssertionError("Emergency assessments must not call the model.");
                };
        AssessmentApiService service = new AssessmentApiService(predictionClient);
        AssessmentRequest request =
                new AssessmentRequest().freeText("severe chest pain").symptomEntries(List.of());

        var response = service.runAssessment(request);

        assertTrue(response.getEmergencyDetected());
        assertNull(response.getResult());
    }
}
