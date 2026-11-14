# feat(api): Assessment API web layer (MDD Iteration 2)

**Base:** `develop` ← **Head:** `feat/assessment-api`

## Summary

Adds the missing HTTP web layer for the Disease Prediction backend: `POST /api/assessments`.
This is MDD Iteration 2 — the backend orchestrates an assessment and delegates prediction to the
Python prediction service across the SOA boundary. The backend does not predict; it validates,
runs an emergency (red-flag) check, and calls the prediction client.

Net-new, non-destructive: the existing domain skeletons and the pre-existing `AssessmentService`
(and the Template Method / Chain of Responsibility / Observer code) are left untouched.

## What's included

- **Controller** — `AssessmentController` (`@RestController`, `POST /api/assessments`): returns 200
  with an `AssessmentResponse`, or 400 when the payload carries no symptom signal.
- **DTOs (immutable records)** — `AssessmentRequest`, `AssessmentResponse`, `PredictionResultDto`,
  `DiseasePredictionDto`, `ExplanationDto`, `SymptomEntryDto`. `AssessmentRequest.hasAnySymptomSignal()`
  encodes the validation rule (valid iff non-blank free text OR at least one symptom entry).
- **Orchestrator** — `AssessmentApiService.runAssessment()`: emergency check runs **before**
  prediction, so an emergency surfaces even if prediction fails. Named `AssessmentApiService` to
  coexist with the pre-existing domain-coupled `AssessmentService` rather than overwriting it.
- **SOA boundary** — `PredictionClient` (interface / port) + `HttpPredictionClient` adapter. The
  adapter calls the Python `POST /predict`, converts checklist symptoms to text, and maps the
  snake-case AI response to the backend contract. It uses a 2-second connect timeout and a
  90-second response timeout.
- **Tests** — `@WebMvcTest AssessmentControllerTest` with a mocked `PredictionClient`.
- **Docs** — Iteration 2 model diagrams (class, sequence, baseline-vs-refined) live in the
  `disease-prediction-docs` repo under `diagrams/mdd/iteration2/` (kept out of the code repo).

## API contract

Request (`POST /api/assessments`):

```json
{
  "patientId": "uuid | null",
  "freeText": "I have a bad cough and high fever for 3 days",
  "symptomEntries": [
    { "rawText": "cough", "severity": "MODERATE", "durationDays": 3, "isMapped": true }
  ]
}
```

Response (200):

```json
{
  "assessmentId": "uuid",
  "emergencyDetected": false,
  "result": {
    "resultId": "uuid",
    "generatedAt": "2026-10-31T12:00:00Z",
    "disclaimer": "This is not a medical diagnosis",
    "predictions": [
      { "rank": 1, "probability": 0.82, "diseaseName": "Common Cold",
        "explanation": { "summary": "...", "contributionWeights": { "cough": 0.5 } } }
    ]
  }
}
```

400 — empty/invalid payload. 422 — no supported symptom could be identified. 502/503 — the
prediction service returned an error or could not be reached.

## How it was tested

- **Unit/web tests:** `./mvnw test` → `Tests run: 4, Failures: 0, Errors: 0` — BUILD SUCCESS
  (3 controller tests: valid→200, empty→400, chest-pain→emergency; plus the existing `contextLoads`).
- **Formatting:** `spotless:apply` applied; `spotless:check` passes (AOSP google-java-format).
- **Runtime smoke (live, packaged jar on port 8080):**
  - valid request → **200** with the correct contract body
  - empty payload → **400**
  - `"severe chest pain"` → **200** with `emergencyDetected=true`

## Notes

- Built locally against JDK 21 (JetBrains Runtime) with `-Dmaven.compiler.release=21` because no
  JDK 25 is installed on the dev machine; the `pom.xml` pins `java.version=25` and CI uses that
  toolchain.
- Branch created from the latest `origin/develop`; per the repo's CI rule, this PR targets
  `develop` (PRs into `main` must come from `develop`).

## Follow-up

The client returns the five highest-ranked predictions. Emergency assessments skip the model and
return `result: null` so the safety response remains available if the model is not ready. Whether
`GET /api/assessments/{id}` is needed to retrieve a stored result remains open.
