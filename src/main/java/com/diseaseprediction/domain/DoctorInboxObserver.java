package com.diseaseprediction.domain;

public class DoctorInboxObserver implements ReportObserver {

    private final Doctor doctor;

    public DoctorInboxObserver(Doctor doctor) {
        this.doctor = doctor;
    }

    @Override
    public void onReportReady(Report report) {
        doctor.onReportReady(report);
    }
}
