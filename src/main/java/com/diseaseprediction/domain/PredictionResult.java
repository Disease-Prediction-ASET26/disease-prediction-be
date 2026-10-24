package com.diseaseprediction.domain;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PredictionResult {

    private UUID resultId;
    private LocalDateTime generatedAt;
    private String disclaimer = "This is not a medical diagnosis";
    private List<DiseasePrediction> predictions;

    public List<DiseasePrediction> getTopPredictions(int n) {
        return null;
    }

    public String getRecommendedSpecialty() {
        return null;
    }
}
