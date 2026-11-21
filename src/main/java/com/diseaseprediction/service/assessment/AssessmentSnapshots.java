package com.diseaseprediction.service.assessment;

import com.diseaseprediction.api.model.FollowupAnswer;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Turns the health data of a stored assessment into the JSON documents kept (encrypted) in its
 * columns, and back (MDD Iteration 7). This is the DTO-to-storage boundary: the entity only holds
 * strings, so the stored result is exactly the {@link PredictionResult} the patient saw.
 */
@Component
public class AssessmentSnapshots {

    /** What the patient submitted. */
    public record AssessmentInput(String freeText, List<SymptomEntry> symptomEntries) {
        public AssessmentInput {
            symptomEntries = symptomEntries == null ? List.of() : List.copyOf(symptomEntries);
        }
    }

    /** The follow-up loop so far: kept on the server, so the client cannot change it. */
    public record FollowUpState(
            List<String> confirmedSymptoms,
            List<String> deniedSymptoms,
            List<FollowupAnswer> answers) {
        public FollowUpState {
            confirmedSymptoms = copy(confirmedSymptoms);
            deniedSymptoms = copy(deniedSymptoms);
            answers = answers == null ? List.of() : List.copyOf(answers);
        }

        /** The state after the first prediction: its active symptoms are known present. */
        public static FollowUpState start(PredictionResult result) {
            return new FollowUpState(
                    result == null ? List.of() : result.getActiveSymptoms(), List.of(), List.of());
        }

        /** Adds a yes to the confirmed symptoms or a no to the denied ones. */
        public FollowUpState answer(FollowupAnswer answer) {
            LinkedHashSet<String> confirmed = new LinkedHashSet<>(confirmedSymptoms);
            LinkedHashSet<String> denied = new LinkedHashSet<>(deniedSymptoms);
            if (Boolean.TRUE.equals(answer.getPresent())) {
                denied.remove(answer.getSymptom());
                confirmed.add(answer.getSymptom());
            } else {
                confirmed.remove(answer.getSymptom());
                denied.add(answer.getSymptom());
            }
            List<FollowupAnswer> allAnswers = new ArrayList<>(answers);
            allAnswers.add(answer);
            return new FollowUpState(List.copyOf(confirmed), List.copyOf(denied), allAnswers);
        }

        /**
         * After a re-ranked prediction: symptoms the service reports as active are added to the
         * confirmed ones. Nothing the patient confirmed is dropped, and a denied symptom stays
         * denied.
         */
        public FollowUpState after(PredictionResult result) {
            if (result == null
                    || result.getActiveSymptoms() == null
                    || result.getActiveSymptoms().isEmpty()) {
                return this;
            }
            LinkedHashSet<String> confirmed = new LinkedHashSet<>(confirmedSymptoms);
            confirmed.addAll(copy(result.getActiveSymptoms()));
            deniedSymptoms.forEach(confirmed::remove);
            return new FollowUpState(List.copyOf(confirmed), deniedSymptoms, answers);
        }

        private static List<String> copy(List<String> symptoms) {
            return symptoms == null
                    ? List.of()
                    : symptoms.stream().filter(s -> s != null && !s.isBlank()).toList();
        }
    }

    private final JsonMapper json;

    public AssessmentSnapshots(JsonMapper json) {
        this.json = json;
    }

    public String write(Object snapshot) {
        return snapshot == null ? null : json.writeValueAsString(snapshot);
    }

    public AssessmentInput readInput(String stored) {
        return json.readValue(stored, AssessmentInput.class);
    }

    public FollowUpState readState(String stored) {
        return json.readValue(stored, FollowUpState.class);
    }

    public PredictionResult readResult(String stored) {
        return stored == null ? null : json.readValue(stored, PredictionResult.class);
    }
}
