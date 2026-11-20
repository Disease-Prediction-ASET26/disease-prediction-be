package com.diseaseprediction.security.access;

import com.diseaseprediction.domain.enums.AccessOutcome;
import com.diseaseprediction.exception.ApiProblemException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/**
 * The outcome and reason code recorded for one guarded call. A 401 or 403 means the caller was
 * refused ({@link AccessOutcome#DENIED}); any other exception means an allowed call that failed
 * ({@link AccessOutcome#FAILED}). Reason codes are problem codes or exception class names, never
 * exception messages, so they cannot carry personal data.
 */
record AccessVerdict(AccessOutcome outcome, String reasonCode) {

    static final AccessVerdict ALLOWED = new AccessVerdict(AccessOutcome.ALLOWED, null);

    private static final int MAX_REASON_LENGTH = 64;

    static AccessVerdict of(Throwable failure) {
        return switch (failure) {
            case null -> new AccessVerdict(AccessOutcome.FAILED, null);
            case ApiProblemException problem -> {
                int status = problem.getStatus().value();
                AccessOutcome outcome =
                        status == 401 || status == 403
                                ? AccessOutcome.DENIED
                                : AccessOutcome.FAILED;
                yield new AccessVerdict(outcome, truncate(problem.getCode()));
            }
            case AccessDeniedException ignored ->
                    new AccessVerdict(AccessOutcome.DENIED, "access-denied");
            case AuthenticationException ignored ->
                    new AccessVerdict(AccessOutcome.DENIED, "unauthenticated");
            default ->
                    new AccessVerdict(
                            AccessOutcome.FAILED, truncate(failure.getClass().getSimpleName()));
        };
    }

    private static String truncate(String reason) {
        if (reason == null || reason.isEmpty()) {
            return null;
        }
        return reason.length() <= MAX_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_REASON_LENGTH);
    }
}
