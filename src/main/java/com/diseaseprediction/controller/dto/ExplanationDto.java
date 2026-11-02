package com.diseaseprediction.controller.dto;

import java.util.Map;

/**
 * Wire representation of a prediction explanation.
 *
 * <p>MDD Iteration 2 — model element: {@code DiseasePrediction.explanation}.
 */
public record ExplanationDto(String summary, Map<String, Double> contributionWeights) {}
