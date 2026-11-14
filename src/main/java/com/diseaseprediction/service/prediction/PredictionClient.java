package com.diseaseprediction.service.prediction;

import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;

import java.util.List;

/**
 * SOA boundary abstraction: the backend's view of the Python prediction service.
 *
 * <p>MDD Iteration 2 — model element: {@code PredictionClient} (interface). Modelling the remote
 * call behind an interface lets the backend be built and tested against the API contract whether or
 * not the live {@code POST /predict} endpoint exists yet (Strategy / port-and-adapter).
 */
public interface PredictionClient {

    /**
     * Delegate prediction to the prediction service.
     *
     * @param freeText raw patient free-text description (may be blank)
     * @param symptomEntries structured, severity-weighted symptom entries
     * @return the ranked prediction result produced by the prediction service
     */
    PredictionResult predict(String freeText, List<SymptomEntry> symptomEntries);
}
