package com.diseaseprediction.service;

import com.diseaseprediction.domain.Assessment;
import com.diseaseprediction.domain.PredictionResult;

public abstract class AssessmentProcessor {

    public final PredictionResult process(Assessment assessment) {
        validate(assessment);
        analyze(assessment);
        return buildResult(assessment);
    }

    protected abstract void validate(Assessment assessment);

    protected abstract void analyze(Assessment assessment);

    protected abstract PredictionResult buildResult(Assessment assessment);
}
