package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.Severity;

import java.util.UUID;

public class SymptomEntry {

    private UUID entryId;
    private String rawText;
    private Severity severity;
    private int durationDays;
    private boolean isMapped;

    public Symptom mapToSymptom() {
        return new Symptom();
    }
}
