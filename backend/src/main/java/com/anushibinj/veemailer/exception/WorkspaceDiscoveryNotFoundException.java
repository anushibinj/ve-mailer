package com.anushibinj.veemailer.exception;

import lombok.Getter;

/**
 * Thrown when workspace metadata discovery successfully reaches the ValueEdge shared
 * space endpoint but the requested Workspace ID is not present in the returned list.
 * Carries the raw JSON response body so the frontend can present it in a troubleshooting
 * panel and let the administrator adjust credentials or the Workspace ID and retry.
 */
@Getter
public class WorkspaceDiscoveryNotFoundException extends RuntimeException {

    private final String rawResponse;

    public WorkspaceDiscoveryNotFoundException(String message, String rawResponse) {
        super(message);
        this.rawResponse = rawResponse;
    }
}
