package com.diseaseprediction.controller.dto;

/**
 * Wire representation of a single ranked disease prediction.
 *
 * <p>MDD Iteration 2 — model element: {@code DiseasePrediction} (FR-P2: ranked likely diseases with
 * a calibrated probability score).
 */
public record DiseasePredictionDto(
        int rank, double probability, String diseaseName, ExplanationDto explanation) {}
