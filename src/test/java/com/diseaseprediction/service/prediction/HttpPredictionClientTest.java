package com.diseaseprediction.service.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

class HttpPredictionClientTest {

    @Test
    void sendsTextAndMapsSnakeCaseAiResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prediction.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://prediction.test/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {"text":"I have a cough. moderate cough for 3 days","top_k":5}
                                        """))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "disclaimer": "This is not a medical diagnosis.",
                                  "active_symptoms": ["cough"],
                                  "predictions": [{
                                    "rank": 1,
                                    "disease_name": "Common Cold",
                                    "probability": 0.82,
                                    "specialty": "General Practice",
                                    "explanation": {
                                      "summary": "Cough is a common symptom.",
                                      "contribution_weights": {"cough": 0.5}
                                    }
                                  }]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        HttpPredictionClient client = new HttpPredictionClient(builder.build(), 5);
        SymptomEntry entry =
                new SymptomEntry()
                        .rawText("cough")
                        .severity(com.diseaseprediction.api.model.Severity.MODERATE)
                        .durationDays(3)
                        .isMapped(true);

        PredictionResult result = client.predict("I have a cough", List.of(entry));

        assertNotNull(result.getResultId());
        assertNotNull(result.getGeneratedAt());
        assertEquals("This is not a medical diagnosis.", result.getDisclaimer());
        assertEquals("Common Cold", result.getPredictions().getFirst().getDiseaseName());
        assertEquals(0.82, result.getPredictions().getFirst().getProbability());
        assertEquals(
                Map.of("cough", 0.5),
                result.getPredictions().getFirst().getExplanation().getContributionWeights());
        server.verify();
    }

    @Test
    void mapsFollowUpFieldsFromPredictResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prediction.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://prediction.test/predict"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "disclaimer": "This is not a medical diagnosis.",
                                  "active_symptoms": ["itching"],
                                  "needs_follow_up": true,
                                  "follow_up": {
                                    "symptom": "nodal_skin_eruptions",
                                    "question": "Do you also have nodal skin eruptions?",
                                    "top_disease": "Fungal infection",
                                    "confidence": 0.48
                                  },
                                  "predictions": [{
                                    "rank": 1,
                                    "disease_name": "Fungal infection",
                                    "probability": 0.48
                                  }]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        HttpPredictionClient client = new HttpPredictionClient(builder.build(), 5);

        PredictionResult result = client.predict("itching", List.of());

        assertEquals(List.of("itching"), result.getActiveSymptoms());
        assertEquals(true, result.getNeedsFollowUp());
        assertNotNull(result.getFollowUp());
        assertEquals("nodal_skin_eruptions", result.getFollowUp().getSymptom());
        assertEquals("Do you also have nodal skin eruptions?", result.getFollowUp().getQuestion());
        assertEquals("Fungal infection", result.getFollowUp().getTopDisease());
        assertEquals(0.48, result.getFollowUp().getConfidence());
        server.verify();
    }

    @Test
    void predictFollowUpPostsConfirmedAndDeniedSymptoms() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prediction.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://prediction.test/predict/followup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {
                                          "confirmed_symptoms": ["itching", "nodal_skin_eruptions"],
                                          "denied_symptoms": [],
                                          "answer": null,
                                          "rounds_used": 1,
                                          "top_k": 5
                                        }
                                        """))
                .andRespond(
                        withSuccess(
                                """
                                {
                                  "disclaimer": "This is not a medical diagnosis.",
                                  "active_symptoms": ["itching", "nodal_skin_eruptions"],
                                  "needs_follow_up": false,
                                  "follow_up": null,
                                  "predictions": [{
                                    "rank": 1,
                                    "disease_name": "Fungal infection",
                                    "probability": 0.97
                                  }]
                                }
                                """,
                                MediaType.APPLICATION_JSON));

        HttpPredictionClient client = new HttpPredictionClient(builder.build(), 5);

        PredictionResult result =
                client.predictFollowUp(List.of("itching", "nodal_skin_eruptions"), List.of(), 1);

        assertEquals(false, result.getNeedsFollowUp());
        assertEquals("Fungal infection", result.getPredictions().getFirst().getDiseaseName());
        assertEquals(0.97, result.getPredictions().getFirst().getProbability());
        server.verify();
    }
}
