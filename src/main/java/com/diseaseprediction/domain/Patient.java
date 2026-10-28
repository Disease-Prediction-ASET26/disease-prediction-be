package com.diseaseprediction.domain;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Patient extends User {

    private LocalDate dateOfBirth;
    private boolean consentGiven;
    private LocalDateTime consentDate;

    public void giveConsent() {}

    public void revokeConsent() {}

    public Assessment startAssessment() {
        return null;
    }

    public void forwardReport(Report report, Doctor doctor) {}

    public File exportData() {
        return null;
    }

    public void deleteAccount() {}
}
