package com.diseaseprediction.controller.dto;

import java.util.UUID;

/**
 * Outbound contract for {@code POST /api/assessments}.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentResponse}.
 */
public record AssessmentResponse(
        UUID assessmentId, boolean emergencyDetected, PredictionResultDto result) {}
