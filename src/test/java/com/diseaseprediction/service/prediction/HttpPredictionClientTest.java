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
}
