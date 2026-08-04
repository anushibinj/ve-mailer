package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.SubscriptionCreateDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.SubscriptionUpdateDto;
import com.anushibinj.veemailer.dto.UserSummaryDto;
import com.anushibinj.veemailer.dto.WorkspaceAdminAssignRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceAdminResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceDiscoveryRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceDiscoveryResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceDuplicateCheckResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceRefetchMetadataResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.service.SubscriptionService;
import com.anushibinj.veemailer.service.UserQueryService;
import com.anushibinj.veemailer.service.WorkspaceAdminService;
import com.anushibinj.veemailer.service.WorkspaceDiscoveryService;
import com.anushibinj.veemailer.service.WorkspaceService;
import com.anushibinj.veemailer.model.TriageSlaThreshold;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceDiscoveryService workspaceDiscoveryService;
    private final SubscriptionService subscriptionService;
    private final WorkspaceAdminService workspaceAdminService;
    private final UserQueryService userQueryService;

    // --- Workspace reads (any authenticated user) ---

    @GetMapping
    public ResponseEntity<List<WorkspaceResponseDto>> getWorkspaces() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = workspaceAdminService.isGlobalAdmin(authentication);
        boolean isWsAdmin = workspaceAdminService.hasWorkspaceAdminRole(authentication);

        List<WorkspaceResponseDto> result;
        if (isAdmin) {
            result = workspaceService.findAllForAdmin();
        } else if (isWsAdmin) {
            // WORKSPACE_ADMIN sees their administered workspaces (including DRAFT)
            List<UUID> administeredIds = workspaceAdminService.getAdministeredWorkspaceIds(authentication.getName());
            result = workspaceService.findAllForWorkspaceAdmin(administeredIds);
        } else {
            result = workspaceService.findAllForUser();
        }
        markMyWorkspaceAdmin(result, authentication);
        return ResponseEntity.ok(result);
    }

    /**
     * Workspace Management admin tab endpoint. Global ADMINs see ALL workspaces including
     * DISABLED. WORKSPACE_ADMINs see ONLY the workspaces they personally administer (any status)
     * — unlike {@link #getWorkspaces()}, this never unions in every other ENABLED workspace in
     * the system, since this endpoint backs the management view where every action (Edit,
     * Delete, Manage Admins) requires workspace-level admin permission.
     */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<List<WorkspaceResponseDto>> getAllWorkspaces() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        List<WorkspaceResponseDto> result;
        if (workspaceAdminService.isGlobalAdmin(authentication)) {
            result = workspaceService.findAll();
        } else {
            List<UUID> administeredIds = workspaceAdminService.getAdministeredWorkspaceIds(authentication.getName());
            result = workspaceService.findAllAdministeredOnly(administeredIds);
        }
        markMyWorkspaceAdmin(result, authentication);
        return ResponseEntity.ok(result);
    }

    /**
     * Fetches a single workspace by ID. DRAFT workspaces are only visible to global ADMINs and
     * to WORKSPACE_ADMINs who administer that specific workspace; everyone else only ever sees
     * ENABLED workspaces here (mirroring the visibility rules already applied to the list endpoint).
     */
    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponseDto> getWorkspace(@PathVariable UUID id) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        WorkspaceResponseDto workspace = workspaceService.findById(id);
        if (workspace.getStatus() == WorkspaceStatus.DRAFT
                && !workspaceAdminService.canManageWorkspace(authentication, id)) {
            throw new AccessDeniedException("You are not authorized to view this workspace");
        }
        workspace.setMyWorkspaceAdmin(workspaceAdminService.isWorkspaceAdmin(authentication.getName(), id));
        return ResponseEntity.ok(workspace);
    }

    /**
     * Flags each workspace in the list with whether the currently authenticated user personally
     * administers it, so the frontend can restrict the "Edit workspace" action — and show a
     * "Workspace Admin" badge — to only the workspaces the user actually administers.
     */
    private void markMyWorkspaceAdmin(List<WorkspaceResponseDto> workspaces, Authentication authentication) {
        if (!workspaceAdminService.hasWorkspaceAdminRole(authentication)) {
            return;
        }
        Set<UUID> administeredIds =
                new HashSet<>(workspaceAdminService.getAdministeredWorkspaceIds(authentication.getName()));
        workspaces.forEach(ws -> ws.setMyWorkspaceAdmin(administeredIds.contains(ws.getId())));
    }

    // --- Workspace mutations ---

    /**
     * Creates a workspace. Global ADMINs and WORKSPACE_ADMINs may both create workspaces
     * (no limit on the number of workspaces a WORKSPACE_ADMIN may own). When a WORKSPACE_ADMIN
     * creates a workspace, they are automatically assigned as its workspace admin.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(
            @RequestBody @Valid WorkspaceCreateRequestDto request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        WorkspaceResponseDto created = workspaceService.create(request, authentication.getName());
        if (!workspaceAdminService.isGlobalAdmin(authentication)
                && workspaceAdminService.hasWorkspaceAdminRole(authentication)) {
            workspaceAdminService.autoAssignCreatorAsAdmin(created.getId(), authentication.getName());
            created.setMyWorkspaceAdmin(true);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /**
     * Step 1 of the workspace creation wizard: checks whether a workspace already exists with
     * the exact same (Root URL, Shared Space ID, Workspace ID) combination, without creating
     * anything. Returns 200 with {@code duplicate=false} when clear, or 409 with the existing
     * workspace's details (via {@link com.anushibinj.veemailer.exception.DuplicateWorkspaceException}).
     */
    @GetMapping("/check-duplicate")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<WorkspaceDuplicateCheckResponseDto> checkDuplicateWorkspace(
            @RequestParam String rootUrl,
            @RequestParam String sharedSpaceId,
            @RequestParam String workspaceId) {
        return ResponseEntity.ok(workspaceService.checkDuplicate(rootUrl, sharedSpaceId, workspaceId));
    }

    /**
     * Step 2 of the workspace creation wizard: the frontend never calls the ValueEdge REST API
     * directly, so this endpoint signs in with the supplied credentials, retrieves the shared
     * space's workspace list, matches the requested Workspace ID, and returns the parsed
     * Title/Shortcode (or a 404 with the raw response when no match is found).
     */
    @PostMapping("/discover-metadata")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<WorkspaceDiscoveryResponseDto> discoverWorkspaceMetadata(
            @RequestBody @Valid WorkspaceDiscoveryRequestDto request) {
        return ResponseEntity.ok(workspaceDiscoveryService.discover(request));
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
            WorkspaceResponseDto updated = workspaceService.updateAsWorkspaceAdmin(id, request);
            updated.setMyWorkspaceAdmin(true);
            return ResponseEntity.ok(updated);
        }

        WorkspaceResponseDto updated = workspaceService.update(id, request);
        updated.setMyWorkspaceAdmin(workspaceAdminService.isWorkspaceAdmin(authentication.getName(), id));
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable UUID id) {
        workspaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Super Admin-only: re-runs ValueEdge metadata discovery for an existing workspace using its
     * already-stored credentials, and overwrites its Title and Shortcode with the freshly
     * discovered values (reusing the same parsing logic as the workspace creation wizard).
     */
    @PostMapping("/{id}/refetch-metadata")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkspaceRefetchMetadataResponseDto> refetchWorkspaceMetadata(@PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.refetchMetadata(id));
    }

    @PostMapping("/test-connection")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<WorkspaceConnectionTestResponseDto> testWorkspaceConnection(
            @RequestBody @Valid WorkspaceConnectionTestRequestDto request,
            Authentication authentication) {
        if (request.getWorkspaceRecordId() != null
                && !workspaceAdminService.canManageWorkspace(authentication, request.getWorkspaceRecordId())) {
            throw new AccessDeniedException("You are not authorized to test this workspace connection");
        }
        return ResponseEntity.ok(workspaceService.testConnection(request));
    }

    // --- Users list (accessible to ADMIN and workspace's WORKSPACE_ADMIN) ---

    /**
     * Returns all registered system users. Accessible to ADMIN and WORKSPACE_ADMIN
     * who manages the specified workspace, so they can subscribe any user to a filter.
     */
    @GetMapping("/{workspaceId}/users")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<List<UserSummaryDto>> getUsersForWorkspace(
            @PathVariable UUID workspaceId,
            Authentication authentication) {
        if (!workspaceAdminService.canManageWorkspace(authentication, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to manage this workspace");
        }
        return ResponseEntity.ok(userQueryService.getAllUserSummaries());
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
        // Regular users always subscribe themselves. Private filters can still only be subscribed
        // by their owner — see SubscriptionService#createSubscription.
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
                        request.getFilterId(), request.getSchedule(), request.getTriageSlaThreshold()));
    }

    /**
     * Creates (or upserts) a single group subscription row for the specified recipient group.
     * The group's members are expanded dynamically at send time, so editing the group membership
     * automatically affects who gets notified without touching the subscription.
     * Requires ADMIN or WORKSPACE_ADMIN role.
     */
    @PostMapping("/{workspaceId}/subscriptions/bulk-group")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<SubscriptionResponseDTO> createGroupSubscription(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid SubscriptionCreateDto request,
            Authentication authentication) {
        if (!workspaceAdminService.canManageWorkspace(authentication, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to manage subscriptions for this workspace");
        }
        if (request.getGroupId() == null) {
            throw new IllegalArgumentException("groupId is required for group subscriptions");
        }
        SubscriptionResponseDTO created = subscriptionService.createGroupSubscription(
                workspaceId, request.getGroupId(), request.getFilterId(), request.getSchedule(),
                request.getTriageSlaThreshold());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
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
                    subscriptionId, workspaceId, request.getSchedule(), request.getTriageSlaThreshold()));
        }

        return ResponseEntity.ok(subscriptionService.updateSubscription(
                userDetails.getUsername(), subscriptionId, workspaceId, request.getSchedule(),
                request.getTriageSlaThreshold()));
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

    /**
     * Toggles a subscription between ACTIVE and DISABLED.
     * Admins and workspace admins can toggle any subscription; regular users can only toggle their own.
     */
    @PatchMapping("/{workspaceId}/subscriptions/{subscriptionId}/toggle")
    public ResponseEntity<SubscriptionResponseDTO> toggleSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (workspaceAdminService.canViewWorkspaceSubscriptions(authentication, workspaceId)) {
            return ResponseEntity.ok(subscriptionService.toggleSubscriptionByAdmin(subscriptionId, workspaceId));
        }

        return ResponseEntity.ok(subscriptionService.toggleSubscription(
                userDetails.getUsername(), subscriptionId, workspaceId));
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

    // --- Workspace Admin management (global ADMIN, or WORKSPACE_ADMIN for their own workspace) ---

    @GetMapping("/{workspaceId}/admins")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<List<WorkspaceAdminResponseDto>> getWorkspaceAdmins(
            @PathVariable UUID workspaceId, Authentication authentication) {
        if (!workspaceAdminService.canManageWorkspace(authentication, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to manage admins for this workspace");
        }
        return ResponseEntity.ok(workspaceAdminService.getWorkspaceAdmins(workspaceId));
    }

    /**
     * Assigns a workspace admin. Both global ADMINs and WORKSPACE_ADMINs acting on their own
     * (administered) workspace may promote a plain USER/MEMBER into an admin — the target's
     * global role is auto-upgraded to WORKSPACE_ADMIN if needed (see WorkspaceAdminService).
     */
    @PostMapping("/{workspaceId}/admins")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<WorkspaceAdminResponseDto> assignWorkspaceAdmin(
            @PathVariable UUID workspaceId,
            @RequestBody @Valid WorkspaceAdminAssignRequestDto request,
            @AuthenticationPrincipal UserDetails userDetails,
            Authentication authentication) {
        if (!workspaceAdminService.canManageWorkspace(authentication, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to manage admins for this workspace");
        }
        WorkspaceAdminResponseDto result = workspaceAdminService.assignWorkspaceAdmin(
                workspaceId, request, userDetails.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{workspaceId}/admins/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKSPACE_ADMIN')")
    public ResponseEntity<Void> removeWorkspaceAdmin(
            @PathVariable UUID workspaceId,
            @PathVariable UUID userId,
            Authentication authentication) {
        if (!workspaceAdminService.canManageWorkspace(authentication, workspaceId)) {
            throw new AccessDeniedException("You are not authorized to manage admins for this workspace");
        }
        workspaceAdminService.removeWorkspaceAdmin(workspaceId, userId);
        return ResponseEntity.noContent().build();
    }
}
