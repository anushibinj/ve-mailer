package com.anushibinj.veemailer.model;

public enum DeliveryStatus {
    SUCCESS,
    FAILED,
    SKIPPED,
    /** A transient fetch failure is being retried; the row is updated in place on each attempt. */
    RETRYING
}
