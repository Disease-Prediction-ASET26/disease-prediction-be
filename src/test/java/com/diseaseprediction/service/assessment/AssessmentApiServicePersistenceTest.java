package com.diseaseprediction.service.assessment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.diseaseprediction.api.model.AssessmentSummary;
import com.diseaseprediction.api.model.DiseasePrediction;
import com.diseaseprediction.api.model.FollowupAnswer;
import com.diseaseprediction.api.model.FollowupRequest;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.domain.Patient;
import com.diseaseprediction.domain.StoredAssessment;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;
import com.diseaseprediction.repository.PatientRepository;
import com.diseaseprediction.repository.StoredAssessmentRepository;
import com.diseaseprediction.service.assessment.AssessmentSnapshots.FollowUpState;
import com.diseaseprediction.service.prediction.PredictionClient;
import com.diseaseprediction.service.profile.ProfileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The storage-side rules of {@link AssessmentApiService} and {@link AssessmentHistoryService}, with
 * the database and the prediction service mocked.
 */
class AssessmentApiServicePersistenceTest {

    private final AssessmentSnapshots snapshots =
            new AssessmentSnapshots(JsonMapper.builder().build());
    private final PredictionClient prediction = mock(PredictionClient.class);
    private final ProfileService profiles = mock(ProfileService.class);
    private final AssessmentHistoryService history = mock(AssessmentHistoryService.class);
    private final AssessmentApiService service =
            new AssessmentApiService(prediction, profiles, history, snapshots);
    private final Patient patient = new Patient("subject", "a@example.com", "Ana", null);

    @BeforeEach
    void signIn() {
        when(profiles.requirePatient()).thenReturn(patient);
        when(profiles.requireConsentingPatient()).thenReturn(patient);
    }

    @Test
    void aFollowUpForAStoredAssessmentNeedsAFullAnswer() {
        UUID id = UUID.randomUUID();
        for (FollowupAnswer answer :
                Arrays.asList(
                        null,
                        new FollowupAnswer().present(true),
                        new FollowupAnswer().symptom(" ").present(true),
                        new FollowupAnswer().symptom("fever"))) {
            assertThatThrownBy(
                            () ->
                                    service.continueAssessment(
                                            new FollowupRequest().assessmentId(id).answer(answer)))
                    .isInstanceOfSatisfying(
                            ApiProblemException.class,
                            problem -> assertThat(problem.getCode()).isEqualTo("invalid-followup"));
        }
        verify(history, never()).load(any());
    }

    @Test
    void aCompletedAssessmentIsRefusedBeforeThePredictionCall() {
        StoredAssessment done = stored(new FollowUpState(List.of("cough"), null, null), true);
        when(history.load(done.getId())).thenReturn(done);

        assertThatThrownBy(() -> service.continueAssessment(answer(done.getId(), "fever", true)))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        problem -> assertThat(problem.getCode()).isEqualTo("assessment-completed"));
        verify(prediction, never()).predictFollowUp(anyList(), anyList(), anyInt());
    }

    @Test
    void denyingTheLastConfirmedSymptomLeavesNothingToPredictFrom() {
        StoredAssessment open = stored(new FollowUpState(List.of("cough"), null, null), false);
        when(history.load(open.getId())).thenReturn(open);

        assertThatThrownBy(() -> service.continueAssessment(answer(open.getId(), "cough", false)))
                .isInstanceOf(InvalidAssessmentRequestException.class);
        verify(history, never()).recordFollowUp(any(), anyLong(), any(), any(), anyBoolean());
    }

    @Test
    void anOlderClientWithoutAnAnswerIsStoredWithItsOwnRounds() {
        StoredAssessment created = stored(FollowUpState.start(null), true);
        when(history.create(any(), any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(created);
        when(prediction.predictFollowUp(anyList(), anyList(), anyInt()))
                .thenReturn(new PredictionResult().needsFollowUp(true));

        service.continueAssessment(
                new FollowupRequest()
                        .confirmedSymptoms(List.of("cough"))
                        .answer(new FollowupAnswer().symptom(" ").present(true)));

        verify(prediction).predictFollowUp(List.of("cough"), List.of(), 0);
        verify(history).create(any(), any(), any(), eq(0), any(), eq(false));
    }

    @Test
    void summariesCopeWithMissingResultsAndUnrankedPredictions() {
        StoredAssessment noResult = stored(FollowUpState.start(null), false);
        PredictionResult unranked =
                new PredictionResult()
                        .predictions(
                                Arrays.asList(
                                        null,
                                        new DiseasePrediction().diseaseName("Unranked"),
                                        new DiseasePrediction().rank(2).diseaseName("Second")));
        StoredAssessment withUnranked =
                new StoredAssessment(
                        patient,
                        "{}",
                        snapshots.write(FollowUpState.start(null)),
                        0,
                        snapshots.write(unranked),
                        true);
        StoredAssessment noPredictions =
                new StoredAssessment(
                        patient,
                        "{}",
                        snapshots.write(FollowUpState.start(null)),
                        0,
                        snapshots.write(new PredictionResult()),
                        true);
        when(history.listFor(any())).thenReturn(List.of(noResult, withUnranked, noPredictions));

        List<AssessmentSummary> summaries = service.listAssessments();

        assertThat(summaries)
                .extracting(AssessmentSummary::getTopDisease)
                .containsExactly(null, "Second", null);
        assertThat(summaries.get(0).getHighestUrgency()).isNull();
    }

    @Test
    void theHistoryServiceMapsMissingAndConcurrentAssessmentsToProblems() {
        StoredAssessmentRepository repository = mock(StoredAssessmentRepository.class);
        AssessmentHistoryService real =
                new AssessmentHistoryService(repository, mock(PatientRepository.class));
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> real.load(missing))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        problem -> assertThat(problem.getCode()).isEqualTo("assessment-not-found"));

        StoredAssessment done = stored(FollowUpState.start(null), true);
        when(repository.findById(done.getId())).thenReturn(Optional.of(done));
        assertThatThrownBy(() -> real.recordFollowUp(done.getId(), 0, "{}", null, true))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        problem -> assertThat(problem.getCode()).isEqualTo("assessment-completed"));

        StoredAssessment open = stored(FollowUpState.start(null), false);
        when(repository.findById(open.getId())).thenReturn(Optional.of(open));
        when(repository.saveAndFlush(open))
                .thenThrow(new ObjectOptimisticLockingFailureException(StoredAssessment.class, 1));
        assertThatThrownBy(() -> real.recordFollowUp(open.getId(), 0, "{}", null, true))
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        problem -> assertThat(problem.getCode()).isEqualTo("assessment-conflict"));
    }

    @Test
    void aNewReportHasNoDoctorNotesOrSendTimeYet() {
        StoredAssessment assessment =
                new StoredAssessment(
                        new Patient("s", "a@example.com", "Ana", LocalDate.of(1990, 1, 1)),
                        "{}",
                        "{}",
                        0,
                        null,
                        false);

        assertThat(assessment.getReport().getAssessment()).isSameAs(assessment);
        assertThat(assessment.getReport().getDoctor()).isNull();
        assertThat(assessment.getReport().getClinicalNotes()).isNull();
        assertThat(assessment.getReport().getSentAt()).isNull();
        assertThat(assessment.getPatient()).isNotNull();
        assertThat(assessment.getInput()).isEqualTo("{}");
    }

    /** A stored assessment with a fixed id, as the database would return it. */
    private StoredAssessment stored(FollowUpState state, boolean completed) {
        StoredAssessment stored = mock(StoredAssessment.class);
        UUID id = UUID.randomUUID();
        when(stored.getId()).thenReturn(id);
        when(stored.getFollowUpState()).thenReturn(snapshots.write(state));
        when(stored.isCompleted()).thenReturn(completed);
        when(stored.getStatus())
                .thenReturn(
                        completed
                                ? com.diseaseprediction.domain.enums.AssessmentStatus.COMPLETED
                                : com.diseaseprediction.domain.enums.AssessmentStatus.IN_PROGRESS);
        StoredAssessment real = new StoredAssessment(patient, "{}", "{}", 0, null, completed);
        when(stored.getReport()).thenReturn(real.getReport());
        when(stored.getCreatedAt()).thenReturn(real.getCreatedAt());
        when(stored.getUpdatedAt()).thenReturn(real.getUpdatedAt());
        return stored;
    }

    private static FollowupRequest answer(UUID id, String symptom, boolean present) {
        return new FollowupRequest()
                .assessmentId(id)
                .answer(new FollowupAnswer().symptom(symptom).present(present));
    }
}
