package com.diseaseprediction.exception;

import org.springframework.http.HttpStatus;

/**
 * A request the service layer refuses, answered as an RFC 9457 problem with type {@code
 * urn:pocket-doc:problem:<code>} by {@link ProblemExceptionHandler}. The message is shown to the
 * client, so it must not contain personal data.
 */
public class ApiProblemException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiProblemException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ApiProblemException profileNotFound() {
        return new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "profile-not-found",
                "No profile exists for this account yet.");
    }

    public static ApiProblemException profileExists() {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "profile-exists",
                "A profile already exists for this account.");
    }

    public static ApiProblemException profileRequired() {
        return new ApiProblemException(
                HttpStatus.FORBIDDEN,
                "profile-required",
                "Complete your profile before using this feature.");
    }

    public static ApiProblemException consentRequired(HttpStatus status) {
        return new ApiProblemException(
                status,
                "consent-required",
                "Consent to health-data processing is required for this feature.");
    }

    public static ApiProblemException emailNotVerified(HttpStatus status) {
        return new ApiProblemException(
                status, "email-not-verified", "The account has no verified email address.");
    }

    public static ApiProblemException invalidProfile(String message) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-profile", message);
    }

    public static ApiProblemException forbidden() {
        return new ApiProblemException(
                HttpStatus.FORBIDDEN, "forbidden", "Your account is not allowed to do this.");
    }

    /** MDD Iteration 7: the assessment is gone, or never belonged to this patient. */
    public static ApiProblemException assessmentNotFound() {
        return new ApiProblemException(
                HttpStatus.NOT_FOUND,
                "assessment-not-found",
                "No such record exists for this account.");
    }

    public static ApiProblemException assessmentCompleted() {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "assessment-completed",
                "This assessment is already completed. Start a new one.");
    }

    public static ApiProblemException assessmentConflict() {
        return new ApiProblemException(
                HttpStatus.CONFLICT,
                "assessment-conflict",
                "Another answer was saved for this assessment first. Reload it and try again.");
    }

    public static ApiProblemException invalidFollowup(String message) {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, "invalid-followup", message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
