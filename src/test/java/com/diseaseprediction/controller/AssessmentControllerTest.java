package com.diseaseprediction.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.Explanation;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.RedFlag;
import com.diseaseprediction.api.model.Urgency;
import com.diseaseprediction.service.assessment.AssessmentApiService;
import com.diseaseprediction.service.prediction.PredictionClient;
import com.diseaseprediction.service.profile.ProfileService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MDD Iteration 2 — Step 5 validation. Verifies the controller slice against the API contract: a
 * valid request yields 200 with a prediction result body; an empty request yields 400. The 400 now
 * originates in {@link AssessmentApiService} (via {@code InvalidAssessmentRequestException} mapped
 * to {@code HttpStatus.BAD_REQUEST}) rather than from an inline controller check; the external
 * assertions below are unchanged. The {@link PredictionClient} is mocked so the backend slice is
 * tested without a live Python service.
 *
 * <p>Security filters are off here: this slice tests the web contract. Authentication and the
 * consent rule are covered by {@code ProfileApiIntegrationTest} and {@code
 * ApiSecurityIntegrationTest}; the consent check is mocked to pass.
 */
@WebMvcTest(AssessmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({AssessmentApiService.class, AssessmentControllerTest.MockConfig.class})
class AssessmentControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ProfileService profileService;

    static class MockConfig {
        @Bean
        PredictionClient predictionClient() {
            PredictionClient client = org.mockito.Mockito.mock(PredictionClient.class);
            stub(client);
            return client;
        }

        static void stub(PredictionClient client) {
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
            when(client.predictFollowUp(any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                    .thenReturn(
                            new PredictionResult()
                                    .resultId(UUID.randomUUID())
                                    .generatedAt(OffsetDateTime.parse("2026-10-31T12:00:00Z"))
                                    .disclaimer("This is not a medical diagnosis")
                                    .needsFollowUp(false)
                                    .predictions(
                                            List.of(
                                                    new DiseasePrediction()
                                                            .rank(1)
                                                            .probability(0.97)
                                                            .diseaseName("Fungal infection"))));
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

    @Autowired private PredictionClient predictionClient;

    @Test
    void emergencyUrgencyFromThePredictionServiceIsRelayed() throws Exception {
        PredictionResult emergency =
                new PredictionResult()
                        .resultId(UUID.randomUUID())
                        .generatedAt(OffsetDateTime.parse("2026-10-31T12:00:00Z"))
                        .disclaimer("This is not a medical diagnosis")
                        .predictions(List.of())
                        .redFlags(
                                List.of(
                                        new RedFlag()
                                                .name("possible_cardiac_event")
                                                .urgency(Urgency.EMERGENCY)
                                                .message("Call emergency services (112) now.")
                                                .matchedSymptoms(
                                                        List.of("breathlessness", "chest_pain"))))
                        .highestUrgency(Urgency.EMERGENCY);
        org.mockito.Mockito.doReturn(emergency).when(predictionClient).predict(any(), any());
        try {
            String body =
                    """
                    {
                      "symptomEntries": [
                        { "rawText": "chest_pain", "severity": "MODERATE", "durationDays": 0, "isMapped": true },
                        { "rawText": "breathlessness", "severity": "SEVERE", "durationDays": 0, "isMapped": true }
                      ]
                    }
                    """;

            mockMvc.perform(
                            post("/api/assessments")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.emergencyDetected").value(true))
                    .andExpect(jsonPath("$.result.highestUrgency").value("EMERGENCY"))
                    .andExpect(
                            jsonPath("$.result.redFlags[0].name").value("possible_cardiac_event"))
                    .andExpect(
                            jsonPath("$.result.redFlags[0].matchedSymptoms[1]")
                                    .value("chest_pain"));
        } finally {
            org.mockito.Mockito.reset(predictionClient);
            MockConfig.stub(predictionClient);
        }
    }

    @Test
    void noUrgencyFromThePredictionServiceIsNotAnEmergency() throws Exception {
        String body =
                """
                { "freeText": "severe chest pain and sweating", "symptomEntries": [] }
                """;

        mockMvc.perform(
                        post("/api/assessments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergencyDetected").value(false));
    }

    @Test
    void followupReturns200WithUpdatedPrediction() throws Exception {
        String body =
                """
                {
                  "confirmedSymptoms": ["itching"],
                  "deniedSymptoms": [],
                  "answer": { "symptom": "nodal_skin_eruptions", "present": true },
                  "roundsUsed": 0
                }
                """;

        mockMvc.perform(
                        post("/api/assessments/followup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").exists())
                .andExpect(jsonPath("$.result.needsFollowUp").value(false))
                .andExpect(
                        jsonPath("$.result.predictions[0].diseaseName").value("Fungal infection"));
    }

    @Test
    void followupWithNoConfirmedSymptomsReturns400() throws Exception {
        String body =
                """
                { "confirmedSymptoms": [], "deniedSymptoms": [], "roundsUsed": 0 }
                """;

        mockMvc.perform(
                        post("/api/assessments/followup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                .andExpect(status().isBadRequest());
    }
}
