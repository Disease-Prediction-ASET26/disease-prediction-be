package com.diseaseprediction.service;

import com.diseaseprediction.domain.Report;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ReportService {

    private final List<ReportObserver> observers = new ArrayList<>();

    public void subscribe(ReportObserver observer) {
        if (!observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unsubscribe(ReportObserver observer) {
        observers.remove(observer);
    }

    public void notifyObservers(Report report) {
        for (ReportObserver observer : observers) {
            observer.onReportReady(report);
        }
    }
}
