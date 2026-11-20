package com.diseaseprediction.service.assessment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.diseaseprediction.api.model.AssessmentRequest;
import com.diseaseprediction.api.model.AssessmentResponse;
import com.diseaseprediction.api.model.FollowUp;
import com.diseaseprediction.api.model.FollowupAnswer;
import com.diseaseprediction.api.model.FollowupRequest;
import com.diseaseprediction.api.model.PredictionResult;
import com.diseaseprediction.api.model.RedFlag;
import com.diseaseprediction.api.model.SymptomEntry;
import com.diseaseprediction.api.model.Urgency;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;
import com.diseaseprediction.service.prediction.PredictionClient;

import org.junit.jupiter.api.Test;

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

    /** A client whose initial prediction carries the given urgency. */
    private static PredictionClient clientReturning(PredictionResult result) {
        return new PredictionClient() {
            @Override
            public PredictionResult predict(String freeText, List<SymptomEntry> symptomEntries) {
                return result;
            }

            @Override
            public PredictionResult predictFollowUp(
                    List<String> confirmed, List<String> denied, int roundsUsed) {
                return result;
            }
        };
    }

    private static PredictionResult resultWithUrgency(Urgency urgency) {
        return new PredictionResult()
                .predictions(List.of())
                .redFlags(
                        urgency == null
                                ? List.of()
                                : List.of(
                                        new RedFlag()
                                                .name("rule")
                                                .urgency(urgency)
                                                .message("message")
                                                .matchedSymptoms(List.of("chest_pain"))))
                .highestUrgency(urgency);
    }

    private static AssessmentResponse assess(PredictionResult result) {
        AssessmentApiService service = new AssessmentApiService(clientReturning(result));
        return service.runAssessment(
                new AssessmentRequest().freeText("chest pain").symptomEntries(List.of()));
    }

    @Test
    void emergencyUrgencyFromThePredictionServiceSetsEmergencyDetected() {
        PredictionResult result = resultWithUrgency(Urgency.EMERGENCY);

        AssessmentResponse response = assess(result);

        assertTrue(response.getEmergencyDetected());
        // The result is relayed so the client can show the red flags; it leads with the alert.
        assertSame(result, response.getResult());
        assertEquals(Urgency.EMERGENCY, response.getResult().getHighestUrgency());
    }

    @Test
    void urgentUrgencyIsNotAnEmergency() {
        AssessmentResponse response = assess(resultWithUrgency(Urgency.URGENT));

        assertFalse(response.getEmergencyDetected());
        assertEquals(Urgency.URGENT, response.getResult().getHighestUrgency());
    }

    @Test
    void noRedFlagMeansNoEmergencyEvenForAlarmingWords() {
        // The backend keeps no keyword list: "chest pain" alone is not an emergency unless the
        // prediction service says so.
        AssessmentResponse response = assess(resultWithUrgency(null));

        assertFalse(response.getEmergencyDetected());
        assertNull(response.getResult().getHighestUrgency());
    }

    @Test
    void followUpRelaysEmergencyUrgency() {
        AssessmentApiService service =
                new AssessmentApiService(clientReturning(resultWithUrgency(Urgency.EMERGENCY)));

        AssessmentResponse response =
                service.continueAssessment(
                        new FollowupRequest()
                                .confirmedSymptoms(new ArrayList<>(List.of("chest_pain")))
                                .roundsUsed(0)
                                .answer(new FollowupAnswer().symptom("sweating").present(true)));

        assertTrue(response.getEmergencyDetected());
    }

    @Test
    void followUpYesAnswerAddsToConfirmedAndCountsTheRound() {
        RecordingPredictionClient client = new RecordingPredictionClient();
        AssessmentApiService service = new AssessmentApiService(client);

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
        AssessmentApiService service = new AssessmentApiService(client);

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
        AssessmentApiService service = new AssessmentApiService(client);

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
        AssessmentApiService service = new AssessmentApiService(client);

        FollowupRequest request =
                new FollowupRequest().confirmedSymptoms(new ArrayList<>()).roundsUsed(0);

        assertThrows(
                InvalidAssessmentRequestException.class, () -> service.continueAssessment(request));
    }
}
