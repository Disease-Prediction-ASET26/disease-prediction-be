package com.diseaseprediction.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class MLModel {

    private UUID modelId;
    private String version;
    private String algorithm;
    private String calibrationMethod;
    private LocalDateTime trainedAt;
    private boolean isActive;

    public PredictionResult predict(List<SymptomEntry> entries) {
        return null;
    }
}
