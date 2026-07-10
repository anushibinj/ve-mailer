package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single selectable value for a reference field in the Easy Filter Builder.
 * Returned by the /octane/field-values endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OctaneFieldValueDto {

    /** Octane entity ID — stored as the filter criterion value */
    private String id;

    /** Human-readable display name shown in the UI (e.g. "New", "John Smith") */
    private String name;
}
