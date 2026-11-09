package com.diseaseprediction.service.prediction;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.Explanation;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * {@link PredictionClient} that calls the Python prediction service ({@code POST /predict} in
 * disease-prediction-ai) over HTTP.
 *
 * <p>Free text goes in {@code text}; checklist entries go in {@code symptoms} as their canonical
 * names, which the service matches against its vocabulary without NLP. The ranked predictions, with
 * description, precautions, specialty and explanation, are mapped onto the backend's {@link
 * PredictionResult}.
 *
 * <p>Returns {@code null} (no prediction) when the service recognises no symptom (HTTP 422) or
 * cannot be reached, so the assessment, and in particular its emergency flag, still completes.
 */
public class HttpPredictionClient implements PredictionClient {

    private static final Logger log = LoggerFactory.getLogger(HttpPredictionClient.class);

    private final RestClient restClient;

    public HttpPredictionClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PredictionResult predict(String freeText, List<SymptomEntry> symptomEntries) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (freeText != null && !freeText.isBlank()) {
            body.put("text", freeText.trim());
        }
        List<String> symptoms =
                symptomEntries == null
                        ? List.of()
                        : symptomEntries.stream()
                                .filter(Objects::nonNull)
                                .map(SymptomEntry::getRawText)
                                .filter(text -> text != null && !text.isBlank())
                                .toList();
        if (!symptoms.isEmpty()) {
            body.put("symptoms", symptoms);
        }

        try {
            PredictResponse response =
                    restClient
                            .post()
                            .uri("/predict")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(PredictResponse.class);
            return response == null ? null : toResult(response);
        } catch (HttpClientErrorException e) {
            if (!e.getStatusCode().isSameCodeAs(HttpStatus.UNPROCESSABLE_CONTENT)) {
                log.warn("Prediction service rejected the request: {}", e.getStatusCode());
            }
            return null;
        } catch (RestClientException e) {
            log.warn("Prediction service unavailable: {}", e.getMessage());
            return null;
        }
    }

    private static PredictionResult toResult(PredictResponse response) {
        List<DiseasePrediction> predictions =
                response.predictions() == null
                        ? List.of()
                        : response.predictions().stream()
                                .map(HttpPredictionClient::toPrediction)
                                .toList();
        return new PredictionResult()
                .resultId(UUID.randomUUID())
                .generatedAt(OffsetDateTime.now())
                .disclaimer(response.disclaimer())
                .predictions(predictions);
    }

    private static DiseasePrediction toPrediction(PredictionItem item) {
        DiseasePrediction prediction =
                new DiseasePrediction()
                        .rank(item.rank())
                        .probability(item.probability())
                        .diseaseName(item.diseaseName())
                        .description(item.description())
                        .precautions(item.precautions() == null ? List.of() : item.precautions())
                        .specialty(item.specialty());
        if (item.explanation() != null) {
            prediction.explanation(
                    new Explanation()
                            .summary(item.explanation().summary())
                            .contributionWeights(item.explanation().contributionWeights()));
        }
        return prediction;
    }

    /** Wire shape of the Python service's {@code PredictResponse} (snake_case). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PredictResponse(
            List<PredictionItem> predictions,
            @JsonProperty("active_symptoms") List<String> activeSymptoms,
            String disclaimer) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PredictionItem(
            int rank,
            @JsonProperty("disease_name") String diseaseName,
            double probability,
            String specialty,
            String description,
            List<String> precautions,
            ExplanationItem explanation) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExplanationItem(
            String summary,
            @JsonProperty("contribution_weights") Map<String, Double> contributionWeights) {}
}
