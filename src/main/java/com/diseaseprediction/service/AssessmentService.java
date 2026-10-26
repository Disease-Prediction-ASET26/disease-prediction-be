package com.diseaseprediction.service;

import com.diseaseprediction.domain.Assessment;
import com.diseaseprediction.domain.AssessmentBuilder;
import com.diseaseprediction.domain.Doctor;
import com.diseaseprediction.domain.MLModel;
import com.diseaseprediction.domain.Notification;
import com.diseaseprediction.domain.PredictionResult;
import com.diseaseprediction.domain.RedFlagRule;
import com.diseaseprediction.domain.Report;
import com.diseaseprediction.domain.SymptomEntry;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AssessmentService {

    private final MLModel mlModel;
    private final List<RedFlagRule> redFlagRules;

    public AssessmentService(MLModel mlModel, List<RedFlagRule> redFlagRules) {
        this.mlModel = mlModel;
        this.redFlagRules = redFlagRules;
    }

    public PredictionResult runAssessment(String freeText, List<SymptomEntry> symptoms, Doctor recipient) {
        AssessmentBuilder builder = new AssessmentBuilder().withFreeText(freeText);
        for (SymptomEntry entry : symptoms) {
            builder.addSymptomEntry(entry);
        }
        Assessment assessment = builder.build();

        if (assessment.checkRedFlags(redFlagRules)) {
            new Notification().send();
        }

        PredictionResult result = assessment.submit();
        if (result == null) {
            result = mlModel.predict(symptoms);
        }

        Report report = new Report();
        if (recipient != null) {
            report.send(recipient);
        }

        new Notification().send();
        return result;
    }
}
