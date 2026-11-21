package com.diseaseprediction.service.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.FollowupAnswer;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.Severity;
import com.diseaseprediction.api.model.SymptomEntry;
import com.diseaseprediction.api.model.Urgency;
import com.diseaseprediction.domain.Patient;
import com.diseaseprediction.domain.StoredAssessment;
import com.diseaseprediction.service.assessment.AssessmentSnapshots.AssessmentInput;
import com.diseaseprediction.service.assessment.AssessmentSnapshots.FollowUpState;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/** The JSON documents of a stored assessment, and the follow-up state rules. */
class AssessmentSnapshotsTest {

    private final AssessmentSnapshots snapshots =
            new AssessmentSnapshots(JsonMapper.builder().build());

    @Test
    void aResultSurvivesTheRoundTripUnchanged() {
        PredictionResult result =
                new PredictionResult()
                        .generatedAt(OffsetDateTime.parse("2026-11-20T10:00:00Z"))
                        .disclaimer("Not a medical diagnosis.")
                        .predictions(
                                List.of(
                                        new DiseasePrediction()
                                                .rank(1)
                                                .probability(0.7)
                                                .diseaseName("Influenza")))
                        .highestUrgency(Urgency.URGENT);

        assertThat(snapshots.readResult(snapshots.write(result))).isEqualTo(result);
        assertThat(snapshots.readResult(null)).isNull();
        assertThat(snapshots.write(null)).isNull();
    }

    @Test
    void theInputSurvivesTheRoundTrip() {
        AssessmentInput input =
                new AssessmentInput(
                        "cough",
                        List.of(
                                new SymptomEntry()
                                        .rawText("cough")
                                        .severity(Severity.MILD)
                                        .durationDays(2)
                                        .isMapped(true)));

        assertThat(snapshots.readInput(snapshots.write(input))).isEqualTo(input);
        assertThat(new AssessmentInput(null, null).symptomEntries()).isEmpty();
    }

    @Test
    void theFollowUpStateStartsFromTheActiveSymptoms() {
        FollowUpState state =
                FollowUpState.start(new PredictionResult().activeSymptoms(List.of("cough")));

        assertThat(state.confirmedSymptoms()).containsExactly("cough");
        assertThat(FollowUpState.start(null).confirmedSymptoms()).isEmpty();
        assertThat(snapshots.readState(snapshots.write(state))).isEqualTo(state);
    }

    @Test
    void answersMoveSymptomsBetweenConfirmedAndDenied() {
        FollowUpState state = new FollowUpState(List.of("cough"), List.of(), null);

        state = state.answer(new FollowupAnswer().symptom("fever").present(false));
        assertThat(state.deniedSymptoms()).containsExactly("fever");

        state = state.answer(new FollowupAnswer().symptom("fever").present(true));
        assertThat(state.confirmedSymptoms()).containsExactly("cough", "fever");
        assertThat(state.deniedSymptoms()).isEmpty();
        assertThat(state.answers()).hasSize(2);

        state = state.answer(new FollowupAnswer().symptom("cough").present(false));
        assertThat(state.confirmedSymptoms()).containsExactly("fever");
    }

    @Test
    void echoedActiveSymptomsAreAddedButNeverDropConfirmedOrUndoDenied() {
        FollowUpState state = new FollowUpState(List.of("cough", "fever"), List.of("rash"), null);

        assertThat(
                        state.after(
                                        new PredictionResult()
                                                .activeSymptoms(List.of("cough", "chill", "rash")))
                                .confirmedSymptoms())
                .containsExactly("cough", "fever", "chill");
        assertThat(state.after(null)).isSameAs(state);
        assertThat(state.after(new PredictionResult())).isSameAs(state);
        assertThat(state.after(new PredictionResult().activeSymptoms(List.of()))).isSameAs(state);
    }

    @Test
    void blankSymptomsAreDropped() {
        FollowUpState state = new FollowUpState(Arrays.asList("cough", " ", null), null, null);

        assertThat(state.confirmedSymptoms()).containsExactly("cough");
        assertThat(state.deniedSymptoms()).isEmpty();
    }

    @Test
    void aCompletedAssessmentTakesNoMoreAnswers() {
        Patient patient = new Patient("subject", "a@example.com", "Ana", LocalDate.of(1990, 1, 1));
        StoredAssessment assessment = new StoredAssessment(patient, "{}", "{}", 0, null, true);

        assertThat(assessment.getReport().getStatus().name()).isEqualTo("DRAFT");
        assertThatThrownBy(() -> assessment.recordFollowUp("{}", null, true))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new StoredAssessment(patient, "{}", "{}", -1, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eachAnswerCountsARoundAndMovesTheUpdateTime() {
        Patient patient = new Patient("subject", "a@example.com", "Ana", null);
        StoredAssessment assessment = new StoredAssessment(patient, "{}", "{}", 0, null, false);
        OffsetDateTime created = assessment.getUpdatedAt();

        assessment.recordFollowUp("{\"a\":1}", "{}", false);
        assessment.recordFollowUp("{\"a\":2}", "{}", true);

        assertThat(assessment.getFollowUpRounds()).isEqualTo(2);
        assertThat(assessment.isCompleted()).isTrue();
        assertThat(assessment.getUpdatedAt()).isAfter(created);
        assertThat(assessment.getFollowUpState()).isEqualTo("{\"a\":2}");
    }
}
