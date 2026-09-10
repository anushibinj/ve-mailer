package com.anushibinj.veemailer.model;

/**
 * Lifecycle of one {@link ScheduledJobRun}. See the state diagram in the feature design doc:
 * PENDING -claim-> RUNNING -> DISPATCHING -> SUCCEEDED, with AWAITING_RETRY/FAILED/SUPPRESSED
 * side branches for the transient-failure, permanent-failure and gap-suppression paths.
 */
public enum JobRunStatus {
    PENDING,
    RUNNING,
    DISPATCHING,
    AWAITING_RETRY,
    SUCCEEDED,
    FAILED,
    SUPPRESSED
}
