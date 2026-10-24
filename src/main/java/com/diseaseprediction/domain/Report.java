package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.ReportStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class Report {

    private UUID reportId;
    private ReportStatus status = ReportStatus.DRAFT;
    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private List<String> unmappedSymptoms;
    private String clinicalNotes;

    private Assessment assessment;

    public void send(Doctor doctor) {
    }

    public void markOpened() {
    }

    public void markReviewed(String notes) {
    }
}
