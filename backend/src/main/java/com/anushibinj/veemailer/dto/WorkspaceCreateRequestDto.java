package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.WorkspaceStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceCreateRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Workspace shortcode is required")
    private String workspaceShortcode;

    @NotBlank(message = "Shared Space ID is required")
    private String sharedSpaceId;

    @NotBlank(message = "Workspace ID is required")
    private String workspaceId;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    @NotBlank(message = "Client Key is required")
    private String clientKey;

    @NotBlank(message = "Root URL is required")
    private String rootUrl;

    // Optional — defaults to DRAFT if not provided
    private WorkspaceStatus status;
}
