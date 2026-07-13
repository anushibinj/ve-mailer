package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.dto.FilterQueryStringRequest;
import com.anushibinj.veemailer.dto.ParsedFilterQueryResponse;
import com.anushibinj.veemailer.dto.PreviewResponse;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.WorkspaceAdminService;
import com.hpe.adm.nga.sdk.model.EntityModel;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/filters")
@RequiredArgsConstructor
public class FilterController {

    private final FilterService filterService;
    private final WorkspaceAdminService workspaceAdminService;

    @GetMapping
    public ResponseEntity<List<Filter>> getFilters(@PathVariable UUID workspaceId) {
        Authentication authentication = currentAuthentication();
        boolean canManage = canManageWorkspaceTemplates(authentication, workspaceId);
        List<Filter> filters = filterService.getAccessibleFilters(workspaceId, authenticationName(authentication), canManage);
        filters.forEach(f -> applyAccessMetadata(f, authenticationName(authentication), canManage));
        return ResponseEntity.ok(filters);
    }

    // --- Filter template mutations (workspace manager or owner for private filters) ---

    @PostMapping
    public ResponseEntity<Filter> createFilter(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody FilterDto dto) {
        Authentication authentication = currentAuthentication();
        boolean canManage = canManageWorkspaceTemplates(authentication, workspaceId);
        // Ensure the workspaceId in the path is used (DTO may also carry it, path wins)
        dto.setWorkspaceId(workspaceId);
        String ownerEmail = canManage ? null : normalizeEmail(authenticationName(authentication));
        Filter saved = filterService.createFilter(dto, ownerEmail);
        applyAccessMetadata(saved, authenticationName(authentication), canManage);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{filterId}")
    public ResponseEntity<Filter> updateFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId,
            @Valid @RequestBody FilterDto dto) {
        Authentication authentication = currentAuthentication();
        Filter existing = requireFilterWriteAccess(workspaceId, filterId, authentication);
        dto.setWorkspaceId(workspaceId);
        Filter updated = filterService.updateFilter(filterId, dto);
        applyAccessMetadata(updated, authenticationName(authentication),
                canManageWorkspaceTemplates(authentication, workspaceId) || isOwnedByCurrentUser(existing, authenticationName(authentication)));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{filterId}")
    public ResponseEntity<Void> deleteFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        Authentication authentication = currentAuthentication();
        requireFilterWriteAccess(workspaceId, filterId, authentication);
        filterService.deleteFilter(filterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{filterId}/clone")
    public ResponseEntity<FilterDto> cloneFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        Authentication authentication = currentAuthentication();
        requireFilterReadAccess(workspaceId, filterId, authentication);
        FilterDto cloned = filterService.cloneFilter(filterId);
        return ResponseEntity.ok(cloned);
    }

    @PostMapping("/parse-query-string")
    public ResponseEntity<ParsedFilterQueryResponse> parseQueryString(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody FilterQueryStringRequest request) {
        ParsedFilterQueryResponse parsed = filterService.parseFilterQueryString(request.getFilterQueryString());
        return ResponseEntity.ok(parsed);
    }

    @GetMapping("/{filterId}/query-string")
    public ResponseEntity<Map<String, String>> getFilterQueryString(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        Authentication authentication = currentAuthentication();
        requireFilterReadAccess(workspaceId, filterId, authentication);
        String filterQueryString = filterService.getFilterQueryString(filterId);
        return ResponseEntity.ok(Map.of("filterQueryString", filterQueryString));
    }

    @PostMapping("/{filterId}/execute")
    public ResponseEntity<List<EntityModel>> executeFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId) {
        Authentication authentication = currentAuthentication();
        requireFilterReadAccess(workspaceId, filterId, authentication);
        List<EntityModel> results = filterService.executeFilter(filterId, workspaceId);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{filterId}/preview")
    public ResponseEntity<PreviewResponse> previewFilter(
            @PathVariable UUID workspaceId,
            @PathVariable UUID filterId,
            @RequestParam(defaultValue = "10") int limit) {
        Authentication authentication = currentAuthentication();
        requireFilterReadAccess(workspaceId, filterId, authentication);
        PreviewResponse preview = filterService.previewFilter(filterId, workspaceId, limit);
        return ResponseEntity.ok(preview);
    }

    private Filter requireFilterReadAccess(UUID workspaceId, UUID filterId, Authentication authentication) {
        Filter filter = filterService.getFilterInWorkspace(filterId, workspaceId);
        String currentEmail = authenticationName(authentication);
        boolean canManage = canManageWorkspaceTemplates(authentication, workspaceId);
        if (!canManage && !isSharedAdminTemplate(filter) && !isOwnedByCurrentUser(filter, currentEmail)) {
            throw new AccessDeniedException("You are not authorized to view this filter");
        }
        applyAccessMetadata(filter, currentEmail, canManage);
        return filter;
    }

    private Filter requireFilterWriteAccess(UUID workspaceId, UUID filterId, Authentication authentication) {
        Filter filter = filterService.getFilterInWorkspace(filterId, workspaceId);
        String currentEmail = authenticationName(authentication);
        if (canManageWorkspaceTemplates(authentication, workspaceId)) {
            return filter;
        }
        if (!isOwnedByCurrentUser(filter, currentEmail)) {
            throw new AccessDeniedException("You are not authorized to edit this filter");
        }
        return filter;
    }

    private boolean canManageWorkspaceTemplates(Authentication authentication, UUID workspaceId) {
        if (authentication == null || authentication.getName() == null) {
            return false;
        }
        return workspaceAdminService.canManageWorkspaceTemplates(authentication, workspaceId);
    }

    private Authentication currentAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private String authenticationName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return authentication.getName();
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private boolean isOwnedByCurrentUser(Filter filter, String currentEmail) {
        return filter.getOwnerEmail() != null
                && currentEmail != null
                && filter.getOwnerEmail().equalsIgnoreCase(currentEmail);
    }

    private boolean isSharedAdminTemplate(Filter filter) {
        return filter.getOwnerEmail() == null;
    }

    private void applyAccessMetadata(Filter filter, String currentEmail, boolean canManageWorkspace) {
        filter.setAdminManaged(isSharedAdminTemplate(filter));
        filter.setEditable(canManageWorkspace || isOwnedByCurrentUser(filter, currentEmail));
    }
}
