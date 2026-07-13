package com.anushibinj.veemailer.dto;

import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.model.WorkspaceConnectivityStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
import java.time.Instant;

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
    private String rootUrl;
    private WorkspaceStatus status;
    private WorkspaceConnectivityStatus connectivityStatus;
    private Instant connectivityCheckedAt;
    private String connectivityMessage;
}
