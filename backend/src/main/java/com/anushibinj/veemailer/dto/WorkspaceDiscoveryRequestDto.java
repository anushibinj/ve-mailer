package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for the workspace metadata discovery endpoint (Step 2 of the workspace
 * creation wizard). The frontend never calls the ValueEdge REST API directly — it sends
 * connection + credential details here and the backend performs the discovery call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceDiscoveryRequestDto {

    @NotBlank(message = "Root URL is required")
    private String rootUrl;

    @NotBlank(message = "Shared Space ID is required")
    private String sharedSpaceId;

    @NotBlank(message = "Workspace ID is required")
    private String workspaceId;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Client Key is required")
    private String clientKey;
}
