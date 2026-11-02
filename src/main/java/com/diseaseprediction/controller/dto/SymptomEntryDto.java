package com.diseaseprediction.controller.dto;

import com.diseaseprediction.domain.enums.Severity;

/**
 * Wire representation of a single symptom entry in an assessment request.
 *
 * <p>MDD Iteration 2 — model element: {@code AssessmentRequest.symptomEntries[*]}. Mirrors the
 * Python prediction service {@code SymptomEntryDTO} across the SOA boundary.
 */
public record SymptomEntryDto(
        String rawText, Severity severity, int durationDays, boolean isMapped) {}
