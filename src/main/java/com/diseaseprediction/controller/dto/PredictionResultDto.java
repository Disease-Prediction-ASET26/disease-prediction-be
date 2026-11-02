package com.diseaseprediction.controller.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire representation of a prediction result.
 *
 * <p>MDD Iteration 2 — model element: {@code PredictionResult}. This is the value the prediction
 * service returns and the backend relays to the patient.
 */
public record PredictionResultDto(
        UUID resultId,
        Instant generatedAt,
        String disclaimer,
        List<DiseasePredictionDto> predictions) {}
