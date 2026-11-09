package com.diseaseprediction.service.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.Severity;
import com.diseaseprediction.api.model.SymptomEntry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

/** Verifies the HTTP adapter to the Python prediction service against a mocked server. */
class HttpPredictionClientTest {

    private static final String PREDICT_RESPONSE =
            """
            {
              "predictions": [
                {
                  "rank": 1,
                  "disease_name": "Common Cold",
                  "probability": 0.71,
                  "specialty": "General Medicine",
                  "description": "A viral infection of the nose and throat.",
                  "precautions": ["rest", "drink fluids"],
                  "explanation": {
                    "summary": "Driven by cough.",
                    "contribution_weights": {"cough": 0.6}
                  }
                },
                {
                  "rank": 2,
                  "disease_name": "Allergy",
                  "probability": 0.12,
                  "specialty": "Allergy and Immunology",
                  "description": null,
                  "precautions": [],
                  "explanation": null
                }
              ],
              "active_symptoms": ["cough"],
              "disclaimer": "This is not a medical diagnosis."
            }
            """;

    private MockRestServiceServer server;
    private HttpPredictionClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://prediction.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new HttpPredictionClient(builder.build());
    }

    @Test
    void sendsTextAndChecklistSymptomsAndMapsTheResponse() {
        server.expect(requestTo("http://prediction.test/predict"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(
                        content()
                                .json(
                                        """
                                        {"text": "dry cough", "symptoms": ["cough", "high_fever"]}
                                        """,
                                        true))
                .andRespond(withSuccess(PREDICT_RESPONSE, MediaType.APPLICATION_JSON));

        PredictionResult result =
                client.predict(" dry cough ", List.of(entry("cough"), entry("high_fever")));

        server.verify();
        assertThat(result).isNotNull();
        assertThat(result.getResultId()).isNotNull();
        assertThat(result.getGeneratedAt()).isNotNull();
        assertThat(result.getDisclaimer()).isEqualTo("This is not a medical diagnosis.");
        assertThat(result.getPredictions()).hasSize(2);

        DiseasePrediction top = result.getPredictions().get(0);
        assertThat(top.getRank()).isEqualTo(1);
        assertThat(top.getDiseaseName()).isEqualTo("Common Cold");
        assertThat(top.getProbability()).isEqualTo(0.71);
        assertThat(top.getSpecialty()).isEqualTo("General Medicine");
        assertThat(top.getDescription()).isEqualTo("A viral infection of the nose and throat.");
        assertThat(top.getPrecautions()).containsExactly("rest", "drink fluids");
        assertThat(top.getExplanation().getSummary()).isEqualTo("Driven by cough.");
        assertThat(top.getExplanation().getContributionWeights()).containsEntry("cough", 0.6);

        DiseasePrediction second = result.getPredictions().get(1);
        assertThat(second.getDescription()).isNull();
        assertThat(second.getExplanation()).isNull();
    }

    @Test
    void sendsOnlyTextWhenThereAreNoEntries() {
        server.expect(requestTo("http://prediction.test/predict"))
                .andExpect(content().json("{\"text\": \"headache\"}", true))
                .andRespond(withSuccess(PREDICT_RESPONSE, MediaType.APPLICATION_JSON));

        assertThat(client.predict("headache", null)).isNotNull();
        server.verify();
    }

    @Test
    void returnsNoPredictionWhenNoSymptomIsRecognised() {
        server.expect(requestTo("http://prediction.test/predict"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT));

        assertThat(client.predict("hello there", List.of())).isNull();
        server.verify();
    }

    @Test
    void returnsNoPredictionWhenTheServiceFails() {
        server.expect(requestTo("http://prediction.test/predict")).andRespond(withServerError());

        assertThat(client.predict("cough", List.of())).isNull();
        server.verify();
    }

    private static SymptomEntry entry(String canonical) {
        return new SymptomEntry()
                .rawText(canonical)
                .severity(Severity.MODERATE)
                .durationDays(0)
                .isMapped(true);
    }
}
