package com.diseaseprediction.domain;

import java.util.List;
import java.util.UUID;

public class Disease {
    private UUID diseaseId;
    private String name;
    private String description;
    private String specialty;
    private List<Precaution> precautions;

    public List<Precaution> getPrecautions() {
        return precautions;
    }
}
