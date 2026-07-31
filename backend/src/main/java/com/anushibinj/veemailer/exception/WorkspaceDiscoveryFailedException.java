package com.anushibinj.veemailer.exception;

import lombok.Getter;

/**
 * Thrown when a ValueEdge call made during workspace metadata discovery (sign-in or the
 * shared-space workspace list request) fails with an HTTP error response — for example the
 * Shared Space ID does not exist, or the credentials are rejected. Unlike a generic
 * {@link IllegalArgumentException}, this carries the raw ValueEdge response body
 * separately from the human-readable message so the frontend can show a short, clean
 * summary plus an optional collapsible troubleshooting panel, instead of dumping the raw
 * response inline into the message text.
 */
@Getter
public class WorkspaceDiscoveryFailedException extends RuntimeException {

    /** Raw ValueEdge response body, if one was returned; may be {@code null}. */
    private final String rawResponse;

    public WorkspaceDiscoveryFailedException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }
}
