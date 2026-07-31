package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response of the workspace metadata discovery endpoint. When {@code shortcodeDetected}
 * is {@code false}, {@code workspaceShortcode} is the literal string "UNKNOWN" and
 * {@code warning} explains why — the wizard will surface this and the workspace will be
 * created as DRAFT (never ENABLED) until a Super Admin corrects the shortcode.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkspaceDiscoveryResponseDto {
    private String workspaceTitle;
    private String workspaceShortcode;
    private boolean shortcodeDetected;
    private String warning;
}
