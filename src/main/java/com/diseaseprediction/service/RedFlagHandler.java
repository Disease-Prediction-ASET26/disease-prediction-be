package com.diseaseprediction.service;

import com.diseaseprediction.domain.Assessment;
import com.diseaseprediction.domain.RedFlagRule;

public abstract class RedFlagHandler {

    private final RedFlagRule rule;
    private RedFlagHandler next;

    protected RedFlagHandler(RedFlagRule rule) {
        this.rule = rule;
    }

    public RedFlagHandler setNext(RedFlagHandler next) {
        this.next = next;
        return next;
    }

    public RedFlagResult handle(Assessment assessment) {
        if (appliesTo(assessment) && rule.matches(assessment)) {
            return RedFlagResult.emergency(rule.getEmergencyMessage());
        }
        return passToNext(assessment);
    }

    protected RedFlagResult passToNext(Assessment assessment) {
        if (next == null) {
            return RedFlagResult.none();
        }
        return next.handle(assessment);
    }

    protected String describe(Assessment assessment) {
        String text = assessment.getFreeTextDescription();
        return text == null ? "" : text.toLowerCase();
    }

    protected abstract boolean appliesTo(Assessment assessment);
}
