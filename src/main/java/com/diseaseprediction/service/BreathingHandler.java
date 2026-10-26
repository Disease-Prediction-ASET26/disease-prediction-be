package com.diseaseprediction.service;

import com.diseaseprediction.domain.Assessment;
import com.diseaseprediction.domain.RedFlagRule;

public class BreathingHandler extends RedFlagHandler {

    public BreathingHandler(RedFlagRule rule) {
        super(rule);
    }

    @Override
    protected boolean appliesTo(Assessment assessment) {
        String text = describe(assessment);
        return text.contains("shortness of breath") || text.contains("difficulty breathing")
                || text.contains("cannot breathe") || text.contains("can't breathe");
    }
}
