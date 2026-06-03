package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.dto.PreviewResponse;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.WorkspaceAdminService;
import com.hpe.adm.nga.sdk.model.EntityModel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/filters")
@RequiredArgsConstructor
public class FilterController {

    private final FilterRepository filterRepository;
    private final FilterService filterService;
    private final WorkspaceAdminService workspaceAdminService;

    @GetMapping
    public ResponseEntity<List<Filter>> getFilters(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(filterRepository.findByWorkspace_Id(workspaceId));
    }

    // --- Filter template mutations (admin or workspace admin) ---

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<Filter> createFilter(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody FilterDto dto) {
        requireWorkspaceManagement(workspaceId);
        // Ensure the workspaceId in the path is used (DTO may also carry it, path wins)
        dto.setWorkspaceId(workspaceId);
        Filter saved = filterService.createFilter(dto);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{filterId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<Filter> updateFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId,
            @Valid @RequestBody FilterDto dto) {
        requireWorkspaceManagement(workspaceId);
        dto.setWorkspaceId(workspaceId);
        Filter updated = filterService.updateFilter(filterId, dto);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{filterId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<Void> deleteFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        requireWorkspaceManagement(workspaceId);
        filterService.deleteFilter(filterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{filterId}/clone")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<FilterDto> cloneFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        requireWorkspaceManagement(workspaceId);
        FilterDto cloned = filterService.cloneFilter(filterId);
        return ResponseEntity.ok(cloned);
    }

    @PostMapping("/{filterId}/execute")
    public ResponseEntity<List<EntityModel>> executeFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        List<EntityModel> results = filterService.executeFilter(filterId, workspaceId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{filterId}/preview")
    public ResponseEntity<PreviewResponse> previewFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId,
            @RequestParam(defaultValue = "10") int limit) {
        PreviewResponse preview = filterService.previewFilter(filterId, workspaceId, limit);
        return ResponseEntity.ok(preview);
    }

    /**
     * Verifies that the current user can manage templates for this workspace.
     * Throws AccessDeniedException if the user is a WORKSPACE_ADMIN but not assigned to this workspace.
     */
    private void requireWorkspaceManagement(UUID workspaceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!workspaceAdminService.canManageWorkspaceTemplates(authentication, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to manage templates for this workspace");
        }
    }
}
