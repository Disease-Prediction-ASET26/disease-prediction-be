package com.diseaseprediction.security.access;

import static org.assertj.core.api.Assertions.assertThat;

import com.diseaseprediction.domain.enums.AccessOutcome;
import com.diseaseprediction.exception.ApiProblemException;
import com.diseaseprediction.exception.InvalidAssessmentRequestException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

/** How each kind of exception is recorded in the access audit trail. */
class AccessVerdictTest {

    @Test
    void allowedHasNoReason() {
        assertThat(AccessVerdict.ALLOWED).isEqualTo(new AccessVerdict(AccessOutcome.ALLOWED, null));
    }

    @Test
    void a403ProblemIsADenialWithItsCode() {
        assertThat(AccessVerdict.of(ApiProblemException.consentRequired(HttpStatus.FORBIDDEN)))
                .isEqualTo(new AccessVerdict(AccessOutcome.DENIED, "consent-required"));
    }

    @Test
    void a401ProblemIsADenialWithItsCode() {
        assertThat(
                        AccessVerdict.of(
                                new ApiProblemException(
                                        HttpStatus.UNAUTHORIZED,
                                        "session-expired",
                                        "Sign in again.")))
                .isEqualTo(new AccessVerdict(AccessOutcome.DENIED, "session-expired"));
    }

    @Test
    void anyOtherProblemIsAFailureWithItsCode() {
        assertThat(AccessVerdict.of(ApiProblemException.profileNotFound()))
                .isEqualTo(new AccessVerdict(AccessOutcome.FAILED, "profile-not-found"));
        assertThat(AccessVerdict.of(ApiProblemException.consentRequired(HttpStatus.BAD_REQUEST)))
                .isEqualTo(new AccessVerdict(AccessOutcome.FAILED, "consent-required"));
    }

    @Test
    void springSecurityRefusalsAreDenials() {
        assertThat(AccessVerdict.of(new AccessDeniedException("Access Denied")))
                .isEqualTo(new AccessVerdict(AccessOutcome.DENIED, "access-denied"));
        assertThat(AccessVerdict.of(new AuthenticationCredentialsNotFoundException("none")))
                .isEqualTo(new AccessVerdict(AccessOutcome.DENIED, "unauthenticated"));
    }

    @Test
    void otherExceptionsAreFailuresNamedByTheirClassNotTheirMessage() {
        AccessVerdict verdict =
                AccessVerdict.of(new InvalidAssessmentRequestException("Ana Pop has chest pain"));

        assertThat(verdict)
                .isEqualTo(
                        new AccessVerdict(
                                AccessOutcome.FAILED, "InvalidAssessmentRequestException"));
        assertThat(verdict.reasonCode()).doesNotContain("Ana");
    }

    @Test
    void anExceptionWithoutAClassNameHasNoReason() {
        // Anonymous classes have an empty simple name.
        RuntimeException anonymous = new RuntimeException() {};

        assertThat(AccessVerdict.of(anonymous))
                .isEqualTo(new AccessVerdict(AccessOutcome.FAILED, null));
    }

    @Test
    void aProblemWithoutACodeHasNoReason() {
        assertThat(AccessVerdict.of(new ApiProblemException(HttpStatus.FORBIDDEN, null, "No.")))
                .isEqualTo(new AccessVerdict(AccessOutcome.DENIED, null));
    }

    @Test
    void reasonsLongerThanTheColumnAreCut() {
        String longCode = "x".repeat(100);

        assertThat(
                        AccessVerdict.of(
                                        new ApiProblemException(
                                                HttpStatus.FORBIDDEN, longCode, "No."))
                                .reasonCode())
                .isEqualTo("x".repeat(64));
        assertThat(
                        AccessVerdict.of(
                                        new ApiProblemException(
                                                HttpStatus.FORBIDDEN, "x".repeat(64), "No."))
                                .reasonCode())
                .hasSize(64);
    }

    @Test
    void noFailureAtAllIsRecordedAsAFailure() {
        assertThat(AccessVerdict.of(null)).isEqualTo(new AccessVerdict(AccessOutcome.FAILED, null));
    }
}
