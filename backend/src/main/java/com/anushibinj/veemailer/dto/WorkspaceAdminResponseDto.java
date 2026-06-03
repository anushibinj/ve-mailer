package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceAdminResponseDto {
    private UUID id;
    private UUID workspaceId;
    private String workspaceTitle;
    private UUID userId;
    private String userName;
    private String userEmail;
    private LocalDateTime createdAt;
    private String createdBy;
}
