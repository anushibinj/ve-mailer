package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceAdminAssignRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceAdminResponseDto;
import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.model.Role;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceAdminMapping;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.RoleRepository;
import com.anushibinj.veemailer.repository.WorkspaceAdminRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceAdminService {

    private final WorkspaceAdminRepository workspaceAdminRepository;
    private final WorkspaceRepository workspaceRepository;
    private final AppUserRepository appUserRepository;
    private final RoleRepository roleRepository;

    // ── Permission helper methods ─────────────────────────────────────────────

    /**
     * Returns true if the authenticated user is a global ADMIN.
     */
    public boolean isGlobalAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN"));
    }

    /**
     * Returns true if the authenticated user has the WORKSPACE_ADMIN role.
     */
    public boolean hasWorkspaceAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_WORKSPACE_ADMIN"));
    }

    /**
     * Returns true if the user (by email) is a workspace admin for the given workspace.
     */
    public boolean isWorkspaceAdmin(String email, UUID workspaceId) {
        return workspaceAdminRepository.existsByWorkspace_IdAndUser_Email(workspaceId, email);
    }

    /**
     * Returns true if the authenticated user can manage (edit templates, view subscriptions, run schedules)
     * the specified workspace. True for global ADMIN or WORKSPACE_ADMIN assigned to this workspace.
     */
    public boolean canManageWorkspace(Authentication authentication, UUID workspaceId) {
        if (isGlobalAdmin(authentication)) {
            return true;
        }
        if (hasWorkspaceAdminRole(authentication)) {
            return isWorkspaceAdmin(authentication.getName(), workspaceId);
        }
        return false;
    }

    /**
     * Returns true if the user can manage filter templates for the workspace.
     */
    public boolean canManageWorkspaceTemplates(Authentication authentication, UUID workspaceId) {
        return canManageWorkspace(authentication, workspaceId);
    }

    /**
     * Returns true if the user can view subscriptions for the workspace.
     */
    public boolean canViewWorkspaceSubscriptions(Authentication authentication, UUID workspaceId) {
        return canManageWorkspace(authentication, workspaceId);
    }

    /**
     * Returns true if the user can run schedules for the workspace.
     */
    public boolean canRunSchedulesForWorkspace(Authentication authentication, UUID workspaceId) {
        return canManageWorkspace(authentication, workspaceId);
    }

    // ── Workspace admin CRUD ──────────────────────────────────────────────────

    @Transactional
    public WorkspaceAdminResponseDto assignWorkspaceAdmin(UUID workspaceId, WorkspaceAdminAssignRequestDto request, String assignedBy) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        AppUser user = appUserRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getUserId()));

        if (workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(workspaceId, user.getId())) {
            throw new IllegalArgumentException("User is already a workspace admin for this workspace");
        }

        // Ensure the user has the WORKSPACE_ADMIN role
        boolean hasRole = user.getRoles().stream()
                .anyMatch(r -> "WORKSPACE_ADMIN".equals(r.getRoleName()));
        if (!hasRole) {
            Role workspaceAdminRole = roleRepository.findByRoleName("WORKSPACE_ADMIN")
                    .orElseThrow(() -> new IllegalStateException("WORKSPACE_ADMIN role not found in database"));
            user.getRoles().add(workspaceAdminRole);
            appUserRepository.save(user);
        }

        WorkspaceAdminMapping mapping = WorkspaceAdminMapping.builder()
                .workspace(workspace)
                .user(user)
                .createdAt(LocalDateTime.now())
                .createdBy(assignedBy)
                .build();

        WorkspaceAdminMapping saved = workspaceAdminRepository.save(mapping);
        return toResponseDto(saved);
    }

    @Transactional
    public void removeWorkspaceAdmin(UUID workspaceId, UUID userId) {
        if (!workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)) {
            throw new IllegalArgumentException("User is not a workspace admin for this workspace");
        }
        workspaceAdminRepository.deleteByWorkspace_IdAndUser_Id(workspaceId, userId);

        // If user has no more workspace admin assignments, remove the WORKSPACE_ADMIN role
        List<WorkspaceAdminMapping> remaining = workspaceAdminRepository.findByUser_Id(userId);
        if (remaining.isEmpty()) {
            AppUser user = appUserRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
            user.getRoles().removeIf(r -> "WORKSPACE_ADMIN".equals(r.getRoleName()));
            appUserRepository.save(user);
        }
    }

    public List<WorkspaceAdminResponseDto> getWorkspaceAdmins(UUID workspaceId) {
        return workspaceAdminRepository.findByWorkspace_Id(workspaceId).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public List<WorkspaceAdminResponseDto> getWorkspaceAdminsByUser(String email) {
        return workspaceAdminRepository.findByUser_Email(email).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns all workspace IDs that the given user (by email) is an admin of.
     */
    public List<UUID> getAdministeredWorkspaceIds(String email) {
        return workspaceAdminRepository.findByUser_Email(email).stream()
                .map(m -> m.getWorkspace().getId())
                .collect(Collectors.toList());
    }

    private WorkspaceAdminResponseDto toResponseDto(WorkspaceAdminMapping mapping) {
        return WorkspaceAdminResponseDto.builder()
                .id(mapping.getId())
                .workspaceId(mapping.getWorkspace().getId())
                .workspaceTitle(mapping.getWorkspace().getTitle())
                .userId(mapping.getUser().getId())
                .userName(mapping.getUser().getName())
                .userEmail(mapping.getUser().getEmail())
                .createdAt(mapping.getCreatedAt())
                .createdBy(mapping.getCreatedBy())
                .build();
    }
}
