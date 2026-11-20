# Personal Data Access Guard (Lab 9, Problem 2)

Lab requirement: "Implement a component for security or for analysis of SQL statements or for
caching or for debugging or for wacky using AOP." This component is the **security** option. Its
design is in the docs repo, `mdd/iteration6/README.md`, section "Part B — AOP component".

## What it does

Spring Security answers "is this a signed-in user with the patient role?". The guard adds two
rules that cut across every service that touches personal or health data:

- **Consent.** `@RequiresActiveConsent` lets only a signed-in patient with a verified email and an
  active consent call the method. It replaces the direct `profileService.requireConsentingPatient()`
  calls that `AssessmentApiService` made in Iteration 6. The problem codes are unchanged.
- **Audit.** `@AuditedAccess(action, resource)` records every call in `access_audit_events`: the
  actor's user id and role, the action, the resource type, the outcome (`ALLOWED`, `DENIED`,
  `FAILED`) and a reason code. It never stores tokens, names, emails or symptoms.

```java
@AuditedAccess(action = AccessAction.CREATE, resource = ResourceType.ASSESSMENT)
@RequiresActiveConsent
public AssessmentResponse runAssessment(AssessmentRequest request) { ... }
```

| Method | Annotations |
| --- | --- |
| `ProfileService.getProfile` | `@AuditedAccess(READ, PATIENT_PROFILE)` |
| `ProfileService.createPatientProfile` | `@AuditedAccess(CREATE, PATIENT_PROFILE)` |
| `ProfileService.recordConsent` | `@AuditedAccess(UPDATE, CONSENT)` |
| `AssessmentApiService.runAssessment` | `@AuditedAccess(CREATE, ASSESSMENT)`, `@RequiresActiveConsent` |
| `AssessmentApiService.continueAssessment` | `@AuditedAccess(UPDATE, ASSESSMENT)`, `@RequiresActiveConsent` |

## How it works

Code is in `src/main/java/com/diseaseprediction/security/access/`.

- `AccessAuditAspect` (`@Around`, order 1) is the outermost aspect, so it also sees the refusals
  of the inner guards. A 401/403 is recorded as `DENIED`, any other exception as `FAILED`
  (`AccessVerdict`). The exception is rethrown unchanged.
- `ConsentAspect` (`@Before`, order 2) runs the consent check before the method body.
- Both run before Spring's transaction interceptor, so they wrap the business transaction.
- `AuditTrail` saves each event in its own transaction (`REQUIRES_NEW`), so a denial is kept even
  when the caller's transaction rolls back.
- Flyway `V3__create_access_audit_events.sql` creates the table and triggers that reject `UPDATE`,
  `DELETE` and `TRUNCATE`, so the application cannot rewrite its own audit history.
- If an audit row cannot be written, the error is logged and the call's result is returned
  unchanged. The guarded method has already committed by then.

### Limits

- Requests that Spring Security refuses in the filter chain (no token, or a non-patient on
  `POST /api/assessments` and `POST /api/me/consents`) never reach the services, so they are not
  in the audit table.
- Spring AOP proxies only see calls from outside the bean. A guarded method called from the same
  class is not guarded.
- Not built yet from the design: `@OwnedResource` with the `OwnershipResolver` strategy (it needs
  stored assessments and `GET /api/assessments/{id}`, planned for Iteration 7) and the
  `DeniedAccessMonitor` stretch goal.
- Open points from the design, still to decide: what happens to audit rows when an account is
  deleted (FR-G2), and who may read the audit trail.

## Tests

- `AccessGuardAspectsTest`: the aspects woven by Spring AOP around a test bean. Covers the aspect
  order, each outcome and reason code, and that an audit failure changes neither a result nor a
  refusal.
- `AccessAuditIntegrationTest`: real security chain and PostgreSQL. Covers the audit rows for a
  whole patient session, consent and role denials, callers without a profile, no personal data in
  rows, a denial kept after a rollback, and the append-only triggers.

Run with `./mvnw clean test` (Docker must be running).

## Use of AI tools

- **Tool:** Kiro IDE (agent mode).
- **Model:** Claude Opus 5.5.
- **Date:** 2026-11-20.

### Prompts

The design (docs repo, `mdd/iteration6/README.md`, Part B) was written earlier. The prompts below
are the implementation session, in order, with the assistant's answers summarised.

1. > Problem 2 [15 points] – optional – lab 9. Implement a component for security or for analysis
   > of SQL statements or for caching or for debugging or for wacky using AOP. [...] i remember
   > talkin about this, to do a feature, can you reminde be what aspect we will do?

   The assistant found the Part B design in the docs repo and summarised it: the audit, consent and
   ownership aspects, the audit trail, the append-only table and the denial monitor.

2. > but dont we already have a check about consent?

   The assistant confirmed that `ProfileService.requireConsentingPatient()` already exists and is
   called directly by `runAssessment` and `continueAssessment`, and that the design meant the
   aspect to replace those calls. It also pointed out that the audit aspect must treat a 403
   `ApiProblemException` as a denial, not only `AccessDeniedException`.

3. > ok, so lets replace them with the aspects

   The assistant implemented the consent and audit parts on branch `feat/aop-access-guard`, wrote
   the tests and this document. The ownership aspect and the denial monitor were left out because
   no endpoint reads a stored record yet.

### How the output was used

The code, the migration, the tests and this document were generated by the assistant in that
session. Changes made against the design during implementation:

- The annotations are on service methods, not controller methods (the repo's `AGENTS.md` keeps
  rules in the service layer). `createPatientProfile` and `continueAssessment` are audited too.
- `ConsentAspect` reuses `ProfileService.requireConsentingPatient()` instead of its own check, so
  the existing problem codes and tests stay valid.
- The request id from the design is not stored: the backend has no request-id mechanism yet.
- Two integration tests first failed because Spring Security refuses non-patients on
  `POST /api/assessments` before any aspect runs. They were changed to use the follow-up endpoint,
  and this is listed under "Limits".

[Team: record your own review and any changes you make after this session here.]
