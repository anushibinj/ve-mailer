package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.model.EntityModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

    static final String CLIENT_KEY_PLACEHOLDER = "(unchanged)";

    private final WorkspaceRepository workspaceRepository;
    private final OctaneCacheService octaneCacheService;

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
     * Returns workspaces visible to workspace admins: only those in the given list of IDs
     * that are ENABLED or DRAFT.
     */
    public List<WorkspaceResponseDto> findAllForWorkspaceAdmin(List<UUID> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return List.of();
        }
        return workspaceRepository.findByIdInAndStatusIn(workspaceIds,
                List.of(WorkspaceStatus.ENABLED, WorkspaceStatus.DRAFT)).stream()
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

    /**
     * Restricted update for WORKSPACE_ADMIN — can only edit: rootUrl, sharedSpaceId,
     * workspaceId, clientId, clientKey. Cannot change title or status.
     */
    public WorkspaceResponseDto updateAsWorkspaceAdmin(UUID id, WorkspaceUpdateRequestDto request) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));

        // WORKSPACE_ADMIN can only update these fields
        workspace.setRootUrl(request.getRootUrl());
        workspace.setSharedSpaceId(request.getSharedSpaceId());
        workspace.setWorkspaceId(request.getWorkspaceId());
        workspace.setClientId(request.getClientId());

        // Only replace clientKey when the caller provides a real new value
        String newKey = request.getClientKey();
        if (newKey != null && !newKey.isBlank() && !CLIENT_KEY_PLACEHOLDER.equals(newKey)) {
            workspace.setClientKey(newKey);
        }

        // title and status are intentionally NOT updated
        return toResponseDto(workspaceRepository.save(workspace));
    }

    public void delete(UUID id) {
        if (!workspaceRepository.existsById(id)) {
            throw new IllegalArgumentException("Workspace not found: " + id);
        }
        workspaceRepository.deleteById(id);
    }

    public WorkspaceConnectionTestResponseDto testConnection(WorkspaceConnectionTestRequestDto request) {
        String sharedSpaceId = request.getSharedSpaceId().trim();
        String workspaceId = request.getWorkspaceId().trim();
        String clientId = request.getClientId().trim();
        String rootUrl = request.getRootUrl().trim();
        String clientKey = resolveClientKey(request.getClientKey(), request.getWorkspaceRecordId());

        int parsedSharedSpaceId = parseId(sharedSpaceId, "Shared Space ID");
        int parsedWorkspaceId = parseId(workspaceId, "Workspace ID");

        Octane octane = octaneCacheService.getOctaneClient(
                rootUrl,
                clientId,
                clientKey,
                parsedSharedSpaceId,
                parsedWorkspaceId
        );

        final OctaneCollection<EntityModel> stories;
        try {
            stories = octane.entityList("stories")
                    .get()
                    .addFields("id")
                    .limit(1)
                    .execute();
        } catch (RuntimeException ex) {
            log.error(
                    "Connection test failed while fetching stories via SDK [rootUrl={}, sharedSpaceId={}, workspaceId={}, clientId={}]: {}",
                    rootUrl, sharedSpaceId, workspaceId, clientId, ex.getMessage(), ex);
            String details = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Please check server URL and credentials."
                    : ex.getMessage();
            throw new IllegalArgumentException("Connection test failed: unable to fetch stories. " + details, ex);
        }

        if (stories == null || stories.isEmpty()) {
            log.warn("Connection test passed but no stories were returned [workspaceId={}]", workspaceId);
            return WorkspaceConnectionTestResponseDto.builder()
                    .success(true)
                    .hasData(false)
                    .workspaceId(workspaceId)
                    .message("Connection successful, but no data was returned from the server.")
                    .build();
        }

        return WorkspaceConnectionTestResponseDto.builder()
                .success(true)
                .hasData(true)
                .workspaceId(workspaceId)
                .message("Connection successful")
                .build();
    }

    private String resolveClientKey(String providedClientKey, UUID workspaceRecordId) {
        if (providedClientKey != null) {
            String trimmed = providedClientKey.trim();
            if (!trimmed.isBlank() && !CLIENT_KEY_PLACEHOLDER.equals(trimmed)) {
                return trimmed;
            }
        }

        if (workspaceRecordId == null) {
            throw new IllegalArgumentException("Client Key is required");
        }

        Workspace workspace = workspaceRepository.findById(workspaceRecordId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceRecordId));

        if (workspace.getClientKey() == null || workspace.getClientKey().isBlank()) {
            throw new IllegalArgumentException("Client Key is not configured for this workspace");
        }
        return workspace.getClientKey();
    }

    private int parseId(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(fieldName + " must be a valid integer");
        }
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
