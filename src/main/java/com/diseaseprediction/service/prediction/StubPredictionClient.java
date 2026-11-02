package com.diseaseprediction.service.prediction;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.Explanation;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link PredictionClient} used until the live HTTP call to the Python prediction service
 * is wired up.
 *
 * <p>MDD Iteration 2 — realization of {@code PredictionClient}. It returns a deterministic,
 * contract-shaped {@link PredictionResult} so the backend slice (controller → service → client) can
 * be built, run and tested end-to-end against the API contract across the SOA boundary before
 * {@code POST /predict} is available. Iteration 2, Step 6 notes replacing this with an {@code
 * HttpPredictionClient} (e.g. Spring {@code RestClient}) that calls the Python service.
 */
@Component
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
