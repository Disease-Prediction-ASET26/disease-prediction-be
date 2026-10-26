package com.diseaseprediction.service;

import com.diseaseprediction.domain.Assessment;
import com.diseaseprediction.domain.PredictionResult;

public class StandardAssessmentProcessor extends AssessmentProcessor {

    @Override
    protected void validate(Assessment assessment) {
        if (assessment == null) {
            throw new IllegalArgumentException("assessment must not be null");
        }
    }

    @Override
    protected void analyze(Assessment assessment) {
        String description = assessment.getFreeTextDescription();
        if (description == null || description.isBlank()) {
            throw new IllegalStateException("assessment has no symptom description to analyze");
        }
    }

    @Override
    protected PredictionResult buildResult(Assessment assessment) {
        PredictionResult result = assessment.submit();
        if (result == null) {
            result = new PredictionResult();
        }
        return result;
    }
}
