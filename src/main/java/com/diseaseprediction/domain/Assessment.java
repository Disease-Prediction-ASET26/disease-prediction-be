package com.diseaseprediction.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Assessment {

    private UUID assessmentId;
    private LocalDateTime createdAt;
    private String freeTextDescription;
    private boolean emergencyDetected = false;
    private List<SymptomEntry> symptomEntries;
    private PredictionResult result;

    public Assessment() {
    }

    Assessment(UUID assessmentId, LocalDateTime createdAt, String freeTextDescription,
               List<SymptomEntry> symptomEntries) {
        this.assessmentId = assessmentId;
        this.createdAt = createdAt;
        this.freeTextDescription = freeTextDescription;
        this.symptomEntries = symptomEntries;
    }

    public void addSymptomEntry(SymptomEntry entry) {
    }

    public boolean checkRedFlags(List<RedFlagRule> rules) {
        return false;
    }

    public PredictionResult submit() {
        return null;
    }
}
