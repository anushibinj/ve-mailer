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
    private boolean hasData;
    private String workspaceId;
    private String message;
}
