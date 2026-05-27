package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    static final String CLIENT_KEY_PLACEHOLDER = "(unchanged)";

    private final WorkspaceRepository workspaceRepository;

    /**
     * Returns workspaces visible to normal (non-admin) users: only ENABLED.
     */
    public List<WorkspaceResponseDto> findAllForUser() {
        return workspaceRepository.findByStatusIn(List.of(WorkspaceStatus.ENABLED)).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns workspaces visible to admin users: ENABLED and DRAFT.
     */
    public List<WorkspaceResponseDto> findAllForAdmin() {
        return workspaceRepository.findByStatusIn(List.of(WorkspaceStatus.ENABLED, WorkspaceStatus.DRAFT)).stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Returns ALL workspaces including DISABLED — for admin management views only.
     */
    public List<WorkspaceResponseDto> findAll() {
        return workspaceRepository.findAll().stream()
                .map(this::toResponseDto)
                .collect(Collectors.toList());
    }

    public WorkspaceResponseDto findById(UUID id) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));
        return toResponseDto(workspace);
    }

    public WorkspaceResponseDto create(WorkspaceCreateRequestDto request) {
        if (workspaceRepository.existsByWorkspaceId(request.getWorkspaceId())) {
            throw new IllegalArgumentException("Workspace ID already exists: " + request.getWorkspaceId());
        }
        WorkspaceStatus status = request.getStatus() != null ? request.getStatus() : WorkspaceStatus.DRAFT;
        Workspace workspace = Workspace.builder()
                .title(request.getTitle())
                .sharedSpaceId(request.getSharedSpaceId())
                .workspaceId(request.getWorkspaceId())
                .clientId(request.getClientId())
                .clientKey(request.getClientKey())
                .rootUrl(request.getRootUrl())
                .status(status)
                .build();
        return toResponseDto(workspaceRepository.save(workspace));
    }

    public WorkspaceResponseDto update(UUID id, WorkspaceUpdateRequestDto request) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));

        workspace.setTitle(request.getTitle());
        workspace.setSharedSpaceId(request.getSharedSpaceId());
        workspace.setWorkspaceId(request.getWorkspaceId());
        workspace.setClientId(request.getClientId());
        workspace.setRootUrl(request.getRootUrl());
        workspace.setStatus(request.getStatus());

        // Only replace clientKey when the caller provides a real new value
        String newKey = request.getClientKey();
        if (newKey != null && !newKey.isBlank() && !CLIENT_KEY_PLACEHOLDER.equals(newKey)) {
            workspace.setClientKey(newKey);
        }

        return toResponseDto(workspaceRepository.save(workspace));
    }

    public void delete(UUID id) {
        if (!workspaceRepository.existsById(id)) {
            throw new IllegalArgumentException("Workspace not found: " + id);
        }
        workspaceRepository.deleteById(id);
    }

    private WorkspaceResponseDto toResponseDto(Workspace workspace) {
        return WorkspaceResponseDto.builder()
                .id(workspace.getId())
                .title(workspace.getTitle())
                .sharedSpaceId(workspace.getSharedSpaceId())
                .workspaceId(workspace.getWorkspaceId())
                .clientId(workspace.getClientId())
                // Never return the real clientKey
                .clientKey(CLIENT_KEY_PLACEHOLDER)
                .clientKeyConfigured(workspace.getClientKey() != null && !workspace.getClientKey().isBlank())
                .rootUrl(workspace.getRootUrl())
                .status(workspace.getStatus())
                .build();
    }
}
