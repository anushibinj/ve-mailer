package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Trivial success body for the workspace duplicate pre-check endpoint (Step 1 of the
 * workspace creation wizard). A conflict is instead reported as a 409 with
 * {@link WorkspaceConflictErrorResponse} — this DTO only ever carries {@code duplicate=false}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceDuplicateCheckResponseDto {
    private boolean duplicate;
}
