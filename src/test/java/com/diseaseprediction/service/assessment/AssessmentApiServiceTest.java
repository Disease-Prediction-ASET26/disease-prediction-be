package com.diseaseprediction.service.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.api.model.FollowUp;
import com.diseaseprediction.api.model.FollowupAnswer;
import com.diseaseprediction.api.model.FollowupRequest;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.SymptomEntry;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;
import com.diseaseprediction.service.prediction.PredictionClient;
import com.diseaseprediction.service.profile.ProfileService;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

class AssessmentApiServiceTest {

    /** Records what the follow-up path forwarded to the prediction client. */
    private static final class RecordingPredictionClient implements PredictionClient {
        List<String> lastConfirmed;
        List<String> lastDenied;
        int lastRounds = -1;
        PredictionResult followUpResult = new PredictionResult();

        @Override
        public PredictionResult predict(String freeText, List<SymptomEntry> symptomEntries) {
            throw new AssertionError("predict() must not be called on the follow-up path.");
        }

        @Override
        public PredictionResult predictFollowUp(
                List<String> confirmedSymptoms, List<String> deniedSymptoms, int roundsUsed) {
            this.lastConfirmed = new ArrayList<>(confirmedSymptoms);
            this.lastDenied = new ArrayList<>(deniedSymptoms);
            this.lastRounds = roundsUsed;
            return followUpResult;
        }
    }

    @Test
    void emergencyAssessmentSkipsPredictionService() {
        PredictionClient predictionClient =
                new PredictionClient() {
                    @Override
                    public PredictionResult predict(
                            String freeText, List<SymptomEntry> symptomEntries) {
                        throw new AssertionError("Emergency assessments must not call the model.");
                    }

                    @Override
                    public PredictionResult predictFollowUp(
                            List<String> confirmed, List<String> denied, int roundsUsed) {
                        throw new AssertionError("Emergency assessments must not call the model.");
                    }
                };
        AssessmentApiService service =
                new AssessmentApiService(predictionClient, Mockito.mock(ProfileService.class));
        AssessmentRequest request =
                new AssessmentRequest().freeText("severe chest pain").symptomEntries(List.of());

        var response = service.runAssessment(request);

        assertTrue(response.getEmergencyDetected());
        assertNull(response.getResult());
    }

    @Test
    void followUpYesAnswerAddsToConfirmedAndCountsTheRound() {
        RecordingPredictionClient client = new RecordingPredictionClient();
        AssessmentApiService service =
                new AssessmentApiService(client, Mockito.mock(ProfileService.class));

        FollowupRequest request =
                new FollowupRequest()
                        .confirmedSymptoms(new ArrayList<>(List.of("cough")))
                        .deniedSymptoms(new ArrayList<>())
                        .roundsUsed(0)
                        .answer(new FollowupAnswer().symptom("high_fever").present(true));

        var response = service.continueAssessment(request);

        assertFalse(response.getEmergencyDetected());
        assertEquals(List.of("cough", "high_fever"), client.lastConfirmed);
        assertEquals(List.of(), client.lastDenied);
        assertEquals(1, client.lastRounds);
    }

    @Test
    void followUpNoAnswerAddsToDeniedAndCountsTheRound() {
        RecordingPredictionClient client = new RecordingPredictionClient();
        AssessmentApiService service =
                new AssessmentApiService(client, Mockito.mock(ProfileService.class));

        FollowupRequest request =
                new FollowupRequest()
                        .confirmedSymptoms(new ArrayList<>(List.of("cough")))
                        .deniedSymptoms(new ArrayList<>())
                        .roundsUsed(1)
                        .answer(new FollowupAnswer().symptom("high_fever").present(false));

        service.continueAssessment(request);

        assertEquals(List.of("cough"), client.lastConfirmed);
        assertEquals(List.of("high_fever"), client.lastDenied);
        assertEquals(2, client.lastRounds);
    }

    @Test
    void followUpWithoutAnswerJustReRanksTheCarriedSymptoms() {
        RecordingPredictionClient client = new RecordingPredictionClient();
        client.followUpResult =
                new PredictionResult()
                        .needsFollowUp(true)
                        .followUp(new FollowUp().symptom("chills").question("Do you have chills?"));
        AssessmentApiService service =
                new AssessmentApiService(client, Mockito.mock(ProfileService.class));

        FollowupRequest request =
                new FollowupRequest()
                        .confirmedSymptoms(new ArrayList<>(List.of("cough")))
                        .roundsUsed(0);

        var response = service.continueAssessment(request);

        assertEquals(List.of("cough"), client.lastConfirmed);
        assertEquals(0, client.lastRounds);
        assertTrue(response.getResult().getNeedsFollowUp());
    }

    @Test
    void followUpWithNoConfirmedSymptomsIsRejected() {
        RecordingPredictionClient client = new RecordingPredictionClient();
        AssessmentApiService service =
                new AssessmentApiService(client, Mockito.mock(ProfileService.class));

        FollowupRequest request =
                new FollowupRequest().confirmedSymptoms(new ArrayList<>()).roundsUsed(0);

        assertThrows(
                InvalidAssessmentRequestException.class, () -> service.continueAssessment(request));
    }
}
