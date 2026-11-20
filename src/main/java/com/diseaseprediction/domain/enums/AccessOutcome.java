package com.diseaseprediction.domain.enums;

/** How a guarded call ended (Personal Data Access Guard). */
public enum AccessOutcome {
    /** The call returned normally. */
    ALLOWED,
    /** The caller was refused: not signed in, wrong role, no profile, no consent, and so on. */
    DENIED,
    /** The call was allowed but failed for another reason (invalid input, downstream error). */
    FAILED
}
