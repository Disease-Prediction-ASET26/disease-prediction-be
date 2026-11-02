package com.diseaseprediction.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised by the service layer when an assessment request carries no symptom signal (no free text
 * and no symptom entries).
 *
 * <p>The validation decision lives in {@code AssessmentApiService}; the web contract (HTTP 400) is
 * preserved declaratively via {@link ResponseStatus}, so controllers stay free of validation logic.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidAssessmentRequestException extends RuntimeException {

    public InvalidAssessmentRequestException(String message) {
        super(message);
    }
}
