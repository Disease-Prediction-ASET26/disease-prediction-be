package com.diseaseprediction.controller.dto;

import java.util.List;
import java.util.UUID;

/**
 * Inbound contract for {@code POST /api/assessments}.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentRequest}. Platform-independent API model
 * that both the Java backend and the Python prediction service are derived from.
 */
public record AssessmentRequest(
        UUID patientId, String freeText, List<SymptomEntryDto> symptomEntries) {

    /**
     * A request is valid when it carries at least one signal to analyse: either non-blank free text
     * or at least one symptom entry. Used to produce a 400 for empty payloads.
     */
    public boolean hasAnySymptomSignal() {
        boolean hasText = freeText != null && !freeText.isBlank();
        boolean hasEntries = symptomEntries != null && !symptomEntries.isEmpty();
        return hasText || hasEntries;
    }
}
