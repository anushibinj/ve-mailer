package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for POST /api/v1/workspaces/{workspaceId}/filters/import.
 *
 * The caller pastes the raw JSON exported from ValueEdge into {@code rawJson}.
 * The remaining fields supply metadata that is absent from the exported JSON.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VeFilterImportDto {

    /** The full ValueEdge exported filter JSON as a string. */
    @NotBlank
    private String rawJson;

    /** Human-readable name for the created filter template. */
    @NotBlank
    private String title;

    /** Optional description. */
    private String description;

    /** Octane entity type, e.g. "defect", "story", "feature". */
    @NotBlank
    private String entityType;
}
