package com.diseaseprediction.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AssessmentBuilder {

    private String freeTextDescription;
    private final List<SymptomEntry> symptomEntries = new ArrayList<>();

    public AssessmentBuilder withFreeText(String freeTextDescription) {
        this.freeTextDescription = freeTextDescription;
        return this;
    }

    public AssessmentBuilder addSymptomEntry(SymptomEntry entry) {
        this.symptomEntries.add(entry);
        return this;
    }

    public Assessment build() {
        return new Assessment(
                UUID.randomUUID(),
                LocalDateTime.now(),
                freeTextDescription,
                new ArrayList<>(symptomEntries)
        );
    }
}
