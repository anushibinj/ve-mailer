package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkspaceAdminAssignRequestDto {

    @NotNull(message = "User ID is required")
    private UUID userId;
}
