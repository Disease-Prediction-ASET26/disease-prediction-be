package com.diseaseprediction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.Explanation;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.service.assessment.AssessmentApiService;
import com.diseaseprediction.service.prediction.PredictionClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MDD Iteration 2 — Step 5 validation. Verifies the controller slice against the API contract: a
 * valid request yields 200 with a prediction result body; an empty request yields 400. The {@link
 * PredictionClient} is mocked so the backend slice is tested without a live Python service.
 */
@WebMvcTest(AssessmentController.class)
@Import({AssessmentApiService.class, AssessmentControllerTest.MockConfig.class})
class AssessmentControllerTest {

    @Autowired private MockMvc mockMvc;

    static class MockConfig {
        @Bean
        PredictionClient predictionClient() {
            PredictionClient client = org.mockito.Mockito.mock(PredictionClient.class);
            PredictionResult stub =
                    new PredictionResult()
                            .resultId(UUID.randomUUID())
                            .generatedAt(OffsetDateTime.parse("2026-10-31T12:00:00Z"))
                            .disclaimer("This is not a medical diagnosis")
                            .predictions(
                                    List.of(
                                            new DiseasePrediction()
                                                    .rank(1)
                                                    .probability(0.82)
                                                    .diseaseName("Common Cold")
                                                    .explanation(
                                                            new Explanation()
                                                                    .summary("stub")
                                                                    .contributionWeights(
                                                                            Map.of(
                                                                                    "cough",
                                                                                    0.5)))));
            when(client.predict(any(), any())).thenReturn(stub);
            return client;
        }
    }

    @Test
    void validRequestReturns200WithPrediction() throws Exception {
        String body =
                """
                {
                  "patientId": "11111111-1111-1111-1111-111111111111",
                  "freeText": "I have a bad cough and high fever for 3 days",
                  "symptomEntries": [
                    { "rawText": "cough", "severity": "MODERATE", "durationDays": 3, "isMapped": true }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").exists())
                .andExpect(jsonPath("$.emergencyDetected").value(false))
                .andExpect(jsonPath("$.result.predictions[0].diseaseName").value("Common Cold"))
                .andExpect(jsonPath("$.result.predictions[0].probability").value(0.82));
    }

    @Test
    void emptyRequestReturns400() throws Exception {
        String body =
                """
                { "patientId": "11111111-1111-1111-1111-111111111111", "freeText": "", "symptomEntries": [] }
                """;

        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chestPainIsFlaggedAsEmergency() throws Exception {
        String body =
                """
                { "freeText": "severe chest pain and sweating", "symptomEntries": [] }
                """;

        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyDetected").value(true));
    }

    @Test
    void checkedChestPainSymptomIsFlaggedAsEmergency() throws Exception {
        String body =
                """
                {
                  "freeText": "",
                  "symptomEntries": [
                    { "rawText": "chest_pain", "severity": "MODERATE", "durationDays": 0, "isMapped": true }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyDetected").value(true));
    }

    @Test
    void checkedBreathlessnessSymptomIsFlaggedAsEmergency() throws Exception {
        String body =
                """
                {
                  "symptomEntries": [
                    { "rawText": "breathlessness", "severity": "SEVERE", "durationDays": 0, "isMapped": true }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyDetected").value(true));
    }

    @Test
    void ordinaryCheckedSymptomIsNotAnEmergency() throws Exception {
        String body =
                """
                {
                  "symptomEntries": [
                    { "rawText": "cough", "severity": "MILD", "durationDays": 0, "isMapped": true }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyDetected").value(false));
    }
}
