package com.anushibinj.veemailer.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.anushibinj.veemailer.dto.OctaneFieldDto;
import com.anushibinj.veemailer.dto.OctaneFieldValueDto;
import com.anushibinj.veemailer.service.OctaneMetadataService;

import lombok.RequiredArgsConstructor;

/**
 * Exposes Octane metadata to power the Easy Filter Builder UI.
 *
 * <ul>
 *   <li>{@code GET .../octane/fields?entityType=defect} — filterable fields with labels and type info</li>
 *   <li>{@code GET .../octane/field-values?fieldName=phase&entityType=defect} — selectable values for a reference field</li>
 * </ul>
 *
 * Both endpoints are accessible to any authenticated user so that regular (non-admin) users
 * can build their own filter subscriptions.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/octane")
@RequiredArgsConstructor
public class OctaneMetadataController {

    private final OctaneMetadataService octaneMetadataService;

    /**
     * Returns all filterable, UI-visible fields for the given Octane entity type.
     * Both common work_item fields and subtype-specific fields are included.
     *
     * @param workspaceId ve-mailer workspace UUID (used to look up Octane credentials)
     * @param entityType  Octane entity subtype, e.g. {@code defect}, {@code story}, {@code feature}
     */
    @GetMapping("/fields")
    public ResponseEntity<List<OctaneFieldDto>> getFilterableFields(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "work_item") String entityType) {
        List<OctaneFieldDto> fields = octaneMetadataService.getFilterableFields(workspaceId, entityType);
        return ResponseEntity.ok(fields);
    }

    /**
     * Returns the selectable values for a reference field so the UI can show
     * a searchable dropdown populated with real names instead of raw Octane IDs.
     *
     * @param workspaceId ve-mailer workspace UUID
     * @param fieldName   Octane field name, e.g. {@code phase}, {@code owner}, {@code severity}
     * @param entityType  Octane entity subtype used to scope phase values
     */
    @GetMapping("/field-values")
    public ResponseEntity<List<OctaneFieldValueDto>> getFieldValues(
            @PathVariable UUID workspaceId,
            @RequestParam String fieldName,
            @RequestParam(defaultValue = "work_item") String entityType) {
        List<OctaneFieldValueDto> values = octaneMetadataService.getFieldValues(workspaceId, fieldName, entityType);
        return ResponseEntity.ok(values);
    }
}
