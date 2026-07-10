package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a single filterable Octane field for display in the Easy Filter Builder.
 * Populated from the Octane /metadata/fields API.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OctaneFieldDto {

    /** Octane API field name (e.g. "phase", "owner", "severity") */
    private String name;

    /** Human-readable label shown in the UI (e.g. "Phase", "Owner", "Severity") */
    private String label;

    /**
     * Octane field type: string | memo | integer | float | boolean | date_time | reference
     * Drives which operator options and value input type the UI shows.
     */
    private String fieldType;

    /** True when this field is a single or multi-reference (points to another entity) */
    private boolean reference;

    /** True when the reference allows selecting multiple values */
    private boolean multiReference;

    /**
     * For reference fields: the Octane entity type that values are fetched from
     * (e.g. "phase", "workspace_user", "list_node", "release", "sprint").
     * Null for non-reference fields.
     */
    private String targetEntityType;

    /**
     * For list_node reference fields: the logical name used to filter list nodes
     * (e.g. "list_node.severity", "list_node.priority").
     * Null for non-list-node targets.
     */
    private String targetLogicalName;
}
