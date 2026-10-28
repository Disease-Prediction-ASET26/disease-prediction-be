package com.diseaseprediction.domain;

import java.util.List;

public class Doctor extends User {

    private String specialty;
    private String licenseNumber;
    private boolean isVerified = false;

    public List<Report> viewInbox() {
        return null;
    }

    public Report openReport(Report report) {
        return null;
    }

    public void addClinicalNotes(Report report, String notes) {}
}
