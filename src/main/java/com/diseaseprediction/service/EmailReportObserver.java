package com.diseaseprediction.service;

import com.diseaseprediction.domain.Doctor;
import com.diseaseprediction.domain.Notification;
import com.diseaseprediction.domain.Report;

public class EmailReportObserver implements ReportObserver {

    private final Doctor doctor;

    public EmailReportObserver(Doctor doctor) {
        this.doctor = doctor;
    }

    @Override
    public void onReportReady(Report report) {
        Notification notification =
                new Notification(
                        "Report ready", "A new report is available for " + doctor.getEmail());
        notification.send();
    }
}
