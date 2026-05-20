package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotBlank;
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
}
