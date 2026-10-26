package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.Severity;

import java.util.UUID;

public class RedFlagRule {

    private UUID ruleId;
    private String name;
    private Severity minSeverity;
    private String emergencyMessage;
    private boolean isActive;

    public boolean matches(Assessment assessment) {
        return false;
    }

    public String getEmergencyMessage() {
        return emergencyMessage;
    }
}
