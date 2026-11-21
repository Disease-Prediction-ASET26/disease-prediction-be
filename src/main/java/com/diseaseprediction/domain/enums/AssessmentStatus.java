package com.diseaseprediction.domain.enums;

/** Where a stored assessment is in the follow-up loop (MDD Iteration 7). */
public enum AssessmentStatus {
    /** The last result asked a follow-up question that is not answered yet. */
    IN_PROGRESS,
    /** No more questions: the result is final, or an emergency ended the loop. */
    COMPLETED
}
