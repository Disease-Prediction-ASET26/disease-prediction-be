package com.diseaseprediction.service.prediction;

import com.diseaseprediction.controller.dto.DiseasePredictionDto;
import com.diseaseprediction.controller.dto.ExplanationDto;
import com.diseaseprediction.controller.dto.PredictionResultDto;
import com.diseaseprediction.controller.dto.SymptomEntryDto;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Default {@link PredictionClient} used until the live HTTP call to the Python prediction service
 * is wired up.
 *
 * <p>MDD Iteration 2 — realization of {@code PredictionClient}. It returns a deterministic,
 * contract-shaped {@link PredictionResultDto} so the backend slice (controller → service → client)
 * can be built, run and tested end-to-end against the API contract across the SOA boundary before
 * {@code POST /predict} is available. Iteration 2, Step 6 notes replacing this with an {@code
 * HttpPredictionClient} (e.g. Spring {@code RestClient}) that calls the Python service.
 */
@Component
public class StubPredictionClient implements PredictionClient {

    @Override
    public PredictionResultDto predict(String freeText, List<SymptomEntryDto> symptomEntries) {
        DiseasePredictionDto top =
                new DiseasePredictionDto(
                        1,
                        0.82,
                        "Common Cold",
                        new ExplanationDto(
                                "Stubbed explanation pending live prediction service.",
                                Map.of("cough", 0.5)));

        return new PredictionResultDto(
                UUID.randomUUID(), Instant.now(), "This is not a medical diagnosis", List.of(top));
    }
}
