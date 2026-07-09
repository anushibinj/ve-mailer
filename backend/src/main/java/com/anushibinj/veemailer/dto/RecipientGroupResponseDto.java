package com.anushibinj.veemailer.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class RecipientGroupResponseDto {

    private UUID id;
    private UUID workspaceId;
    private String name;
    private String description;
    private Set<String> memberEmails;
    private int memberCount;
    private LocalDateTime createdAt;
    private String createdBy;
}
