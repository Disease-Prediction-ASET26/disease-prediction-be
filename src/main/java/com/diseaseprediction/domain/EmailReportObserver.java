package com.diseaseprediction.domain;

public class EmailReportObserver implements ReportObserver {

    private final String address;

    public EmailReportObserver(String address) {
        this.address = address;
    }

    @Override
    public void onReportReady(Report report) {
        Notification notification = new Notification("Report ready", "A new report is available at " + address);
        notification.send();
    }
}
