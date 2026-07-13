package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceConnectionTestRequestDto {

    /**
     * Optional workspace record UUID.
     * Used to authorize WORKSPACE_ADMIN access and resolve existing clientKey when "(unchanged)" is sent.
     */
    private UUID workspaceRecordId;

    @NotBlank(message = "Shared Space ID is required")
    private String sharedSpaceId;

    @NotBlank(message = "Workspace ID is required")
    private String workspaceId;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    /**
     * Optional for edit flows: if blank or "(unchanged)", backend resolves from workspaceRecordId.
     */
    private String clientKey;

    @NotBlank(message = "Root URL is required")
    private String rootUrl;
}
