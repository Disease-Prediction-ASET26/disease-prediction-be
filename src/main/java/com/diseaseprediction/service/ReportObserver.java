package com.diseaseprediction.service;

import com.diseaseprediction.domain.Report;

public interface ReportObserver {

    void onReportReady(Report report);
}
