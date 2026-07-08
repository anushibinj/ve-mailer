package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.SubscriptionCreateDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.SubscriptionUpdateDto;
import com.anushibinj.veemailer.dto.WorkspaceAdminAssignRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceAdminResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.service.SubscriptionService;
import com.anushibinj.veemailer.service.WorkspaceAdminService;
import com.anushibinj.veemailer.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final SubscriptionService subscriptionService;
    private final WorkspaceAdminService workspaceAdminService;

    // --- Workspace reads (any authenticated user) ---

    @GetMapping
    public ResponseEntity<List<WorkspaceResponseDto>> getWorkspaces() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = workspaceAdminService.isGlobalAdmin(authentication);
        boolean isWsAdmin = workspaceAdminService.hasWorkspaceAdminRole(authentication);

        if (isAdmin) {
            return ResponseEntity.ok(workspaceService.findAllForAdmin());
        } else if (isWsAdmin) {
            // WORKSPACE_ADMIN sees their administered workspaces (including DRAFT)
            List<UUID> administeredIds = workspaceAdminService.getAdministeredWorkspaceIds(authentication.getName());
            return ResponseEntity.ok(workspaceService.findAllForWorkspaceAdmin(administeredIds));
        } else {
            return ResponseEntity.ok(workspaceService.findAllForUser());
        }
    }

    /**
     * Admin-only endpoint to list ALL workspaces including DISABLED (for management views).
     */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WorkspaceResponseDto>> getAllWorkspaces() {
        return ResponseEntity.ok(workspaceService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponseDto> getWorkspace(@PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.findById(id));
    }

    // --- Workspace mutations ---

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(
            @RequestBody @Valid WorkspaceCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<WorkspaceResponseDto> updateWorkspace(
            @PathVariable UUID id,
            @RequestBody @Valid WorkspaceUpdateRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = workspaceAdminService.isGlobalAdmin(authentication);

        if (!isAdmin) {
            // WORKSPACE_ADMIN — must be admin of this workspace
            if (!workspaceAdminService.isWorkspaceAdmin(authentication.getName(), id)) {
                throw new AccessDeniedException("You are not a workspace admin for this workspace");
            }
            // Use restricted update for workspace admins
            return ResponseEntity.ok(workspaceService.updateAsWorkspaceAdmin(id, request));
        }

        return ResponseEntity.ok(workspaceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable UUID id) {
        workspaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Subscription read (role-aware: ADMIN/WORKSPACE_ADMIN sees all, MEMBER sees own only) ---

    @GetMapping("/{workspaceId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponseDTO>> getSubscriptions(
            @PathVariable UUID workspaceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean canViewAll = workspaceAdminService.canViewWorkspaceSubscriptions(authentication, workspaceId);
        List<SubscriptionResponseDTO> result = canViewAll
                ? subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId)
                : subscriptionService.getActiveSubscriptionsForUser(authentication.getName(), workspaceId);
        return ResponseEntity.ok(result);
    }

    // --- Subscription CRUD (authenticated user operates on their own subscriptions) ---

    @PostMapping("/{workspaceId}/subscriptions")
    public ResponseEntity<SubscriptionResponseDTO> createSubscription(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid SubscriptionCreateDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Admins and workspace admins may subscribe any user by specifying recipientEmail.
        // Regular users always subscribe themselves.
        String targetEmail = userDetails.getUsername();
        if (request.getRecipientEmail() != null && !request.getRecipientEmail().isBlank()) {
            if (!workspaceAdminService.canViewWorkspaceSubscriptions(authentication, workspaceId)) {
                throw new AccessDeniedException("Only admins and workspace admins can subscribe others");
            }
            targetEmail = request.getRecipientEmail().trim().toLowerCase();
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.createSubscription(
                        targetEmail, workspaceId,
                        request.getFilterId(), request.getSchedule()));
    }

    @PutMapping("/{workspaceId}/subscriptions/{subscriptionId}")
    public ResponseEntity<SubscriptionResponseDTO> updateSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId,
            @RequestBody @Valid SubscriptionUpdateDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Admins and workspace admins may update any subscription in their workspace.
        if (workspaceAdminService.canViewWorkspaceSubscriptions(authentication, workspaceId)) {
            return ResponseEntity.ok(subscriptionService.updateSubscriptionByAdmin(
                    subscriptionId, workspaceId, request.getSchedule()));
        }

        return ResponseEntity.ok(subscriptionService.updateSubscription(
                userDetails.getUsername(), subscriptionId, workspaceId, request.getSchedule()));
    }

    @DeleteMapping("/{workspaceId}/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Admins and workspace admins may delete any subscription in their workspace.
        if (workspaceAdminService.canViewWorkspaceSubscriptions(authentication, workspaceId)) {
            subscriptionService.deleteSubscriptionByAdmin(subscriptionId, workspaceId);
        } else {
            subscriptionService.deleteSubscription(userDetails.getUsername(), subscriptionId, workspaceId);
        }
        return ResponseEntity.noContent().build();
    }

    // --- On-demand run (admin or workspace admin) ---

    @PostMapping("/{workspaceId}/subscriptions/{subscriptionId}/run")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<String> runSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!workspaceAdminService.canRunSchedulesForWorkspace(authentication, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to run schedules for this workspace");
        }
        try {
            subscriptionService.runSubscription(subscriptionId, workspaceId);
            return ResponseEntity.ok("Notification sent successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // --- Workspace Admin management (ADMIN only) ---

    @GetMapping("/{workspaceId}/admins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WorkspaceAdminResponseDto>> getWorkspaceAdmins(
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(workspaceAdminService.getWorkspaceAdmins(workspaceId));
    }

    @PostMapping("/{workspaceId}/admins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkspaceAdminResponseDto> assignWorkspaceAdmin(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceAdminAssignRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        WorkspaceAdminResponseDto result = workspaceAdminService.assignWorkspaceAdmin(
                workspaceId, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{workspaceId}/admins/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeWorkspaceAdmin(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId) {
        workspaceAdminService.removeWorkspaceAdmin(workspaceId, userId);
        return ResponseEntity.noContent().build();
    }
}

