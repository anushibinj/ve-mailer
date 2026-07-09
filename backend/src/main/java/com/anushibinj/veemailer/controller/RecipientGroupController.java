package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.RecipientGroupCreateDto;
import com.anushibinj.veemailer.dto.RecipientGroupMemberDto;
import com.anushibinj.veemailer.dto.RecipientGroupResponseDto;
import com.anushibinj.veemailer.dto.RecipientGroupUpdateDto;
import com.anushibinj.veemailer.service.RecipientGroupService;
import com.anushibinj.veemailer.service.WorkspaceAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Recipient group (team) management endpoints, scoped to a workspace.
 * Reading groups is available to all authenticated users in the workspace.
 * Creating, updating, and deleting groups requires ADMIN or WORKSPACE_ADMIN role.
 */
@RestController
@RequestMapping("/api/v1/workspaces/{workspaceId}/recipient-groups")
@RequiredArgsConstructor
public class RecipientGroupController {

    private final RecipientGroupService recipientGroupService;
    private final WorkspaceAdminService workspaceAdminService;

    // ── Read ─────────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<List<RecipientGroupResponseDto>> getGroups(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(recipientGroupService.getGroupsForWorkspace(workspaceId));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<RecipientGroupResponseDto> getGroup(
            @PathVariable UUID workspaceId,
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(recipientGroupService.getGroup(workspaceId, groupId));
    }

    // ── Write (admin / workspace-admin only) ─────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<RecipientGroupResponseDto> createGroup(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid RecipientGroupCreateDto request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        enforceCanManage(auth, workspaceId);
        RecipientGroupResponseDto result = recipientGroupService.createGroup(
                workspaceId, request, auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @PutMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<RecipientGroupResponseDto> updateGroup(
            @PathVariable UUID workspaceId,
            @PathVariable UUID groupId,
            @RequestBody @Valid RecipientGroupUpdateDto request) {
        enforceCanManage(SecurityContextHolder.getContext().getAuthentication(), workspaceId);
        return ResponseEntity.ok(recipientGroupService.updateGroup(workspaceId, groupId, request));
    }

    @DeleteMapping("/{groupId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable UUID workspaceId,
            @PathVariable UUID groupId) {
        enforceCanManage(SecurityContextHolder.getContext().getAuthentication(), workspaceId);
        recipientGroupService.deleteGroup(workspaceId, groupId);
        return ResponseEntity.noContent().build();
    }

    // ── Member management ────────────────────────────────────────────────────

    @PostMapping("/{groupId}/members")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<RecipientGroupResponseDto> addMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID groupId,
            @RequestBody @Valid RecipientGroupMemberDto request) {
        enforceCanManage(SecurityContextHolder.getContext().getAuthentication(), workspaceId);
        return ResponseEntity.ok(
                recipientGroupService.addMember(workspaceId, groupId, request.getEmail()));
    }

    @DeleteMapping("/{groupId}/members/{email}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<RecipientGroupResponseDto> removeMember(
            @PathVariable UUID workspaceId,
            @PathVariable UUID groupId,
            @PathVariable String email) {
        enforceCanManage(SecurityContextHolder.getContext().getAuthentication(), workspaceId);
        return ResponseEntity.ok(
                recipientGroupService.removeMember(workspaceId, groupId, email));
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void enforceCanManage(Authentication auth, UUID workspaceId) {
        if (!workspaceAdminService.canManageWorkspace(auth, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to manage groups for this workspace");
        }
    }
}
