package com.diseaseprediction.domain;

import java.util.List;
import java.util.UUID;

public class SymptomChecklist {

    private UUID checklistId;
    private String version;
    private SymptomGroup root;

    public List<Symptom> getSymptoms() {
        return root == null ? List.of() : root.getSymptoms();
    }
}
