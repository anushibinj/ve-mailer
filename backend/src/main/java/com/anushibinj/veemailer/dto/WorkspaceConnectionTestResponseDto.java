package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceConnectionTestResponseDto {
    private boolean success;
    private String workspaceId;
    private String workspaceName;
    private String message;
}
