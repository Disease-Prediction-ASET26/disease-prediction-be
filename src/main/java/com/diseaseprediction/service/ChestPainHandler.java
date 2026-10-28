package com.diseaseprediction.service;

import com.diseaseprediction.domain.Assessment;
import com.diseaseprediction.domain.RedFlagRule;

public class ChestPainHandler extends RedFlagHandler {

    public ChestPainHandler(RedFlagRule rule) {
        super(rule);
    }

    @Override
    protected boolean appliesTo(Assessment assessment) {
        String text = describe(assessment);
        return text.contains("chest pain")
                || text.contains("chest pressure")
                || text.contains("tightness in chest");
    }
}
