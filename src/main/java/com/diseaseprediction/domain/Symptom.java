package com.diseaseprediction.domain;

import java.util.List;
import java.util.UUID;


public class Symptom implements SymptomComponent {

    private UUID symptomId;
    private String name;
    private String bodyArea;
    private List<String> synonyms;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public List<Symptom> getSymptoms() {
        return List.of(this);
    }
}
