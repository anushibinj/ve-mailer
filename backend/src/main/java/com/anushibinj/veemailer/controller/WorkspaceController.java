package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
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

    // --- Admin-only workspace CRUD ---

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WorkspaceResponseDto>> getWorkspaces() {
        return ResponseEntity.ok(workspaceService.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkspaceResponseDto> getWorkspace(@PathVariable UUID id) {
        return ResponseEntity.ok(workspaceService.findById(id));
    }

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

    // --- Subscription endpoints (any authenticated user) ---

    @GetMapping("/{workspaceId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponseDTO>> getSubscriptions(
            @PathVariable UUID workspaceId) {
        return ResponseEntity.ok(subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId));
    }

    @PostMapping("/{workspaceId}/subscriptions/{subscriptionId}/run")
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

