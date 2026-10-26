package com.diseaseprediction.config;

import com.diseaseprediction.domain.MLModel;
import com.diseaseprediction.service.ReportService;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AssessmentConfig {

    @Bean
    public MLModel mlModel() {
        return new MLModel();
    }

    @Bean
    public ReportService reportService() {
        return new ReportService();
    }
}
