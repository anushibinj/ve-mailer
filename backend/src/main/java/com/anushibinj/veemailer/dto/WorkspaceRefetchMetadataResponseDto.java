package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the "Refetch workspace metadata" Super Admin action. Re-runs the same
 * ValueEdge discovery + Title/Shortcode parsing used by the workspace creation wizard,
 * using the workspace's already-stored Root URL, Shared Space ID, Workspace ID and
 * credentials, then persists the refreshed Title/Shortcode onto the existing workspace.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceRefetchMetadataResponseDto {
    private WorkspaceResponseDto workspace;
    private boolean shortcodeDetected;
    private String warning;
    /**
     * True when the workspace's shortcode came back UNKNOWN and it had to be automatically
     * downgraded from ENABLED to DRAFT as a result (an ENABLED workspace may never have an
     * unknown shortcode) — the frontend surfaces this so the Super Admin knows visibility
     * changed as a side effect of the refetch.
     */
    private boolean statusDowngradedToDraft;
}
