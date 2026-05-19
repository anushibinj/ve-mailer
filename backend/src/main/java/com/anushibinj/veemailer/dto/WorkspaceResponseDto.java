package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceResponseDto {
    private UUID id;
    private String title;
    private String sharedSpaceId;
    private String workspaceId;
    private String clientId;
    // Always "(unchanged)" — never the real value
    private String clientKey;
    private boolean clientKeyConfigured;
}
