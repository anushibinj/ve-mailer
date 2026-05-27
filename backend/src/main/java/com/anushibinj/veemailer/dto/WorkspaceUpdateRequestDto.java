package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.WorkspaceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceUpdateRequestDto {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Shared Space ID is required")
    private String sharedSpaceId;

    @NotBlank(message = "Workspace ID is required")
    private String workspaceId;

    @NotBlank(message = "Client ID is required")
    private String clientId;

    // Optional — if null, blank, or "(unchanged)", the existing clientKey is preserved
    private String clientKey;

    @NotBlank(message = "Root URL is required")
    private String rootUrl;

    @NotNull(message = "Status is required")
    private WorkspaceStatus status;
}
