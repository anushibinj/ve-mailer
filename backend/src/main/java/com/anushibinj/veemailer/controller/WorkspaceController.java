package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.WorkspaceDto;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceRepository workspaceRepository;
    private final SubscriptionService subscriptionService;

    @GetMapping
    public ResponseEntity<List<WorkspaceDto>> getWorkspaces() {
        List<WorkspaceDto> dtos = workspaceRepository.findAll().stream()
                .map(workspace -> WorkspaceDto.builder()
                        .id(workspace.getId())
                        .title(workspace.getTitle())
                        .sharedSpaceId(workspace.getSharedSpaceId())
                        .workspaceId(workspace.getWorkspaceId())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{workspaceId}/subscriptions")
    public ResponseEntity<List<SubscriptionResponseDTO>> getSubscriptions(@PathVariable UUID workspaceId) {
        return ResponseEntity.ok(subscriptionService.getActiveSubscriptionsForWorkspace(workspaceId));
    }
}
