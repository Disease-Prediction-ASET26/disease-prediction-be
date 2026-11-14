package com.diseaseprediction.service.prediction;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.Explanation;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Calls the Python prediction service and maps its response to the backend API contract. */
@Component
public class HttpPredictionClient implements PredictionClient {

    private static final String DISCLAIMER = "This is not a medical diagnosis.";

    private final RestClient restClient;
    private final int topK;

    @Autowired
    public HttpPredictionClient(
            @Value("${app.prediction.base-url:http://localhost:8000}") String baseUrl,
            @Value("${app.prediction.top-k:5}") int topK) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(90));
        this.restClient =
                RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.topK = topK;
    }

    HttpPredictionClient(RestClient restClient, int topK) {
        this.restClient = restClient;
        this.topK = topK;
    }

    @Override
    public PredictionResult predict(String freeText, List<SymptomEntry> symptomEntries) {
        String text = toPredictionText(freeText, symptomEntries);
        if (text.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Add a symptom description before submitting.");
        }

        AiPredictionResponse response;
        try {
            response =
                    restClient
                            .post()
                            .uri("/predict")
                            .body(new AiPredictionRequest(text, topK))
                            .retrieve()
                            .body(AiPredictionResponse.class);
        } catch (HttpClientErrorException.UnprocessableEntity exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "The prediction service could not identify a supported symptom.",
                    exception);
        } catch (ResourceAccessException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The prediction service is not available. Please try again.",
                    exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The prediction service returned an error.", exception);
        }

        if (response == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "The prediction service returned an empty response.");
        }

        return toPredictionResult(response);
    }

    private static String toPredictionText(String freeText, List<SymptomEntry> symptomEntries) {
        List<String> parts = new ArrayList<>();
        if (freeText != null && !freeText.isBlank()) {
            parts.add(freeText.trim());
        }

        if (symptomEntries != null) {
            for (SymptomEntry entry : symptomEntries) {
                if (entry == null || entry.getRawText() == null || entry.getRawText().isBlank()) {
                    continue;
                }
                StringBuilder phrase = new StringBuilder();
                if (entry.getSeverity() != null) {
                    phrase.append(entry.getSeverity().name().toLowerCase(Locale.ROOT)).append(' ');
                }
                phrase.append(entry.getRawText().trim());
                Integer durationDays = entry.getDurationDays();
                if (durationDays != null && durationDays > 0) {
                    phrase.append(" for ").append(durationDays).append(" days");
                }
                parts.add(phrase.toString());
            }
        }

        return String.join(". ", parts);
    }

    private static PredictionResult toPredictionResult(AiPredictionResponse response) {
        List<DiseasePrediction> predictions =
                response.predictions() == null
                        ? List.of()
                        : response.predictions().stream()
                                .map(HttpPredictionClient::toDiseasePrediction)
                                .toList();

        return new PredictionResult()
                .resultId(UUID.randomUUID())
                .generatedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .disclaimer(
                        response.disclaimer() == null || response.disclaimer().isBlank()
                                ? DISCLAIMER
                                : response.disclaimer())
                .predictions(predictions);
    }

    private static DiseasePrediction toDiseasePrediction(AiPrediction prediction) {
        DiseasePrediction result =
                new DiseasePrediction()
                        .rank(prediction.rank())
                        .probability(prediction.probability())
                        .diseaseName(prediction.diseaseName());
        if (prediction.explanation() != null) {
            AiExplanation explanation = prediction.explanation();
            Map<String, Double> weights =
                    explanation.contributionWeights() == null
                            ? Map.of()
                            : explanation.contributionWeights();
            result.explanation(
                    new Explanation().summary(explanation.summary()).contributionWeights(weights));
        }
        return result;
    }

    private record AiPredictionRequest(
            @JsonProperty("text") String text, @JsonProperty("top_k") int topK) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiPredictionResponse(List<AiPrediction> predictions, String disclaimer) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiPrediction(
            int rank,
            @JsonProperty("disease_name") String diseaseName,
            double probability,
            AiExplanation explanation) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiExplanation(
            String summary,
            @JsonProperty("contribution_weights") Map<String, Double> contributionWeights) {}
}
