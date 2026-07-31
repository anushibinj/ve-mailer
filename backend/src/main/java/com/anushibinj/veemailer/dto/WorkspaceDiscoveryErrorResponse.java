package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Error body returned when workspace metadata discovery cannot find the requested
 * Workspace ID in the ValueEdge shared space's workspace list. Includes the raw
 * ValueEdge response so the frontend can display it in a troubleshooting panel.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceDiscoveryErrorResponse {

    private int status;
    private String error;
    private String message;
    private String rawResponse;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();
}
