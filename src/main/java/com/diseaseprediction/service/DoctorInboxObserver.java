package com.diseaseprediction.service;

import com.diseaseprediction.domain.Doctor;
import com.diseaseprediction.domain.Report;

public class DoctorInboxObserver implements ReportObserver {

    private final Doctor doctor;

    public DoctorInboxObserver(Doctor doctor) {
        this.doctor = doctor;
    }

    @Override
    public void onReportReady(Report report) {
        report.send(doctor);
        doctor.openReport(report);
    }
}
