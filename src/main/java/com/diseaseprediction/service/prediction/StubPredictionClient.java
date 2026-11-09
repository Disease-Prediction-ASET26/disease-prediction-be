package com.diseaseprediction.service.prediction;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.Explanation;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Fallback {@link PredictionClient} used when no prediction service URL is configured ({@code
 * app.prediction.base-url} empty); see {@link PredictionClientConfig}.
 *
 * <p>MDD Iteration 2 — realization of {@code PredictionClient}. It returns a deterministic,
 * contract-shaped {@link PredictionResult} so the backend slice (controller → service → client) can
 * be built, run and tested end-to-end against the API contract across the SOA boundary before
 * {@code POST /predict} is available. The live call is {@link HttpPredictionClient}.
 */
public class StubPredictionClient implements PredictionClient {

    @Override
    public PredictionResult predict(String freeText, List<SymptomEntry> symptomEntries) {
        DiseasePrediction top =
                new DiseasePrediction()
                        .rank(1)
                        .probability(0.82)
                        .diseaseName("Common Cold")
                        .explanation(
                                new Explanation()
                                        .summary(
                                                "Stubbed explanation pending live prediction"
                                                        + " service.")
                                        .contributionWeights(Map.of("cough", 0.5)));

        return new PredictionResult()
                .resultId(UUID.randomUUID())
                .generatedAt(OffsetDateTime.now())
                .disclaimer("This is not a medical diagnosis")
                .predictions(List.of(top));
    }
}
