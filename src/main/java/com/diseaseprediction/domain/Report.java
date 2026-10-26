package com.diseaseprediction.domain;

import com.diseaseprediction.domain.enums.ReportStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
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

    private final List<Doctor> observers = new ArrayList<>();

    public void subscribe(Doctor doctor) {
        if (!observers.contains(doctor)) {
            observers.add(doctor);
        }
    }

    public void unsubscribe(Doctor doctor) {
        observers.remove(doctor);
    }

    public void notifyObservers() {
        for (Doctor doctor : observers) {
            doctor.onReportReady(this);
        }
    }

    public void send(Doctor doctor) {
        subscribe(doctor);
        status = ReportStatus.SENT;
        sentAt = LocalDateTime.now();
        notifyObservers();
    }

    public void markOpened() {
        status = ReportStatus.OPENED;
    }

    public void markReviewed(String notes) {
        clinicalNotes = notes;
        status = ReportStatus.REVIEWED;
        notifyObservers();
    }
}
