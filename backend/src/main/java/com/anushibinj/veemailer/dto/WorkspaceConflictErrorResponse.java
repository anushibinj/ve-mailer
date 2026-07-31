package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Error body returned when workspace creation/update is rejected because a workspace
 * with the exact same (Root URL, Shared Space ID, Workspace ID) combination already exists.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceConflictErrorResponse {

    private int status;
    private String error;
    private String message;
    private ExistingWorkspaceRef existingWorkspace;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExistingWorkspaceRef {
        private UUID id;
        private String name;
        private String rootUrl;
        private String sharedSpaceId;
        private String workspaceId;
    }
}
