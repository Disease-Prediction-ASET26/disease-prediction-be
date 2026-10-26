package com.diseaseprediction.domain;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Component
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
