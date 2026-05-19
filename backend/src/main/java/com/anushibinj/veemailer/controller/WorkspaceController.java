package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.SubscriptionCreateDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.SubscriptionUpdateDto;
import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.service.SubscriptionService;
import com.anushibinj.veemailer.service.WorkspaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    // --- Workspace reads (any authenticated user) ---

    @GetMapping
    public ResponseEntity<List<WorkspaceResponseDto>> getWorkspaces() {
        return ResponseEntity.ok(workspaceService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkspaceResponseDto> getWorkspace(@PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.findById(id));
    }

    // --- Workspace mutations (admin only) ---

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkspaceResponseDto> createWorkspace(
            @RequestBody @Valid WorkspaceCreateRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workspaceService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkspaceResponseDto> updateWorkspace(
            @PathVariable UUID id,
            @RequestBody @Valid WorkspaceUpdateRequestDto request) {
        return ResponseEntity.ok(workspaceService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable UUID id) {
        workspaceService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Subscription read (role-aware: ADMIN sees all, MEMBER sees own only) ---

    @GetMapping("/{workspaceId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponseDTO>> getSubscriptions(
            @PathVariable UUID workspaceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        List<SubscriptionResponseDTO> result = isAdmin
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(subscriptionService.createSubscription(
                        userDetails.getUsername(), workspaceId,
                        request.getFilterId(), request.getSchedule()));
    }

    @PutMapping("/{workspaceId}/subscriptions/{subscriptionId}")
    public ResponseEntity<SubscriptionResponseDTO> updateSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId,
            @RequestBody @Valid SubscriptionUpdateDto request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(subscriptionService.updateSubscription(
                userDetails.getUsername(), subscriptionId, workspaceId, request.getSchedule()));
    }

    @DeleteMapping("/{workspaceId}/subscriptions/{subscriptionId}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId,
            @AuthenticationPrincipal UserDetails userDetails) {
        subscriptionService.deleteSubscription(userDetails.getUsername(), subscriptionId, workspaceId);
        return ResponseEntity.noContent().build();
    }

    // --- On-demand run (admin only) ---

    @PostMapping("/{workspaceId}/subscriptions/{subscriptionId}/run")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> runSubscription(
            @PathVariable UUID workspaceId,
            @PathVariable UUID subscriptionId) {
        try {
            subscriptionService.runSubscription(subscriptionId, workspaceId);
            return ResponseEntity.ok("Notification sent successfully.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }
}

