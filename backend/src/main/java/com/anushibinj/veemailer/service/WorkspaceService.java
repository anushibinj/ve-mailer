package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceDuplicateCheckResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.exception.DuplicateWorkspaceException;
import com.anushibinj.veemailer.model.WorkspaceConnectivityStatus;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.util.UrlNormalizer;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.model.EntityModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceService {

    static final String CLIENT_KEY_PLACEHOLDER = "(unchanged)";
    static final String UNKNOWN_SHORTCODE = "UNKNOWN";
    static final String SUPER_ADMIN_ROLE = "ADMIN";

    static final String SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE =
            "This workspace cannot be moved to the Enabled state because the workspace shortcode "
                    + "could not be identified automatically during creation. Please contact a Super "
                    + "Admin to review and correct the workspace metadata.";

    private final WorkspaceRepository workspaceRepository;
    private final AppUserRepository appUserRepository;
    private final OctaneCacheService octaneCacheService;
    private final EmailService emailService;
    private final NotificationPreferencesService notificationPreferencesService;

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

    /**
     * Runs the same (Root URL, Shared Space ID, Workspace ID) duplicate check used by
     * {@link #create}, without creating anything. Used by Step 1 of the workspace creation
     * wizard so the administrator is warned about a conflict before entering credentials.
     * Throws {@link DuplicateWorkspaceException} (mapped to 409) when a duplicate is found.
     */
    public WorkspaceDuplicateCheckResponseDto checkDuplicate(String rootUrl, String sharedSpaceId, String workspaceId) {
        String normalizedRootUrl = normalizeRootUrl(rootUrl);
        String normalizedSharedSpaceId = sharedSpaceId.trim();
        String normalizedWorkspaceId = workspaceId.trim();
        rejectIfDuplicateCombination(normalizedRootUrl, normalizedSharedSpaceId, normalizedWorkspaceId, null);
        return WorkspaceDuplicateCheckResponseDto.builder().duplicate(false).build();
    }

    public WorkspaceResponseDto create(WorkspaceCreateRequestDto request, String createdByEmail) {
        String normalizedRootUrl = normalizeRootUrl(request.getRootUrl());
        String normalizedSharedSpaceId = request.getSharedSpaceId().trim();
        String normalizedWorkspaceId = request.getWorkspaceId().trim();

        rejectIfDuplicateCombination(normalizedRootUrl, normalizedSharedSpaceId, normalizedWorkspaceId, null);

        WorkspaceStatus status = resolveStatusForCreate(request.getStatus(), request.getWorkspaceShortcode());
        Workspace workspace = Workspace.builder()
                .title(request.getTitle())
                .workspaceShortcode(request.getWorkspaceShortcode())
                .sharedSpaceId(normalizedSharedSpaceId)
                .workspaceId(normalizedWorkspaceId)
                .clientId(request.getClientId())
                .clientKey(request.getClientKey())
                .rootUrl(normalizedRootUrl)
                .status(status)
                .createdAt(Instant.now())
                .createdBy(createdByEmail)
                .build();
        Workspace saved;
        try {
            saved = workspaceRepository.save(workspace);
        } catch (DataIntegrityViolationException ex) {
            // Guards against a race where two concurrent requests pass the pre-check at the
            // same time; the DB-level unique constraint is the ultimate source of truth.
            throw duplicateExceptionFromRace(normalizedRootUrl, normalizedSharedSpaceId, normalizedWorkspaceId, ex);
        }
        if (isShortcodeUnknown(saved.getWorkspaceShortcode())) {
            notifySuperAdminsOfDraftWorkspace(saved);
        } else if (isWorkspaceAdminCreator(createdByEmail)) {
            notifySuperAdminsOfWorkspaceCreatedByWorkspaceAdmin(saved);
        }
        return toResponseDto(refreshConnectivityForWorkspace(saved));
    }

    public WorkspaceResponseDto update(UUID id, WorkspaceUpdateRequestDto request) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));

        String normalizedRootUrl = normalizeRootUrl(request.getRootUrl());
        String normalizedSharedSpaceId = request.getSharedSpaceId().trim();
        String normalizedWorkspaceId = request.getWorkspaceId().trim();
        rejectIfDuplicateCombination(normalizedRootUrl, normalizedSharedSpaceId, normalizedWorkspaceId, id);
        validateStatusChange(request.getStatus(), request.getWorkspaceShortcode());

        workspace.setTitle(request.getTitle());
        workspace.setWorkspaceShortcode(request.getWorkspaceShortcode());
        workspace.setSharedSpaceId(normalizedSharedSpaceId);
        workspace.setWorkspaceId(normalizedWorkspaceId);
        workspace.setClientId(request.getClientId());
        workspace.setRootUrl(normalizedRootUrl);
        workspace.setStatus(request.getStatus());

        // Only replace clientKey when the caller provides a real new value
        String newKey = request.getClientKey();
        if (newKey != null && !newKey.isBlank() && !CLIENT_KEY_PLACEHOLDER.equals(newKey)) {
            workspace.setClientKey(newKey);
        }

        Workspace saved;
        try {
            saved = workspaceRepository.save(workspace);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateExceptionFromRace(normalizedRootUrl, normalizedSharedSpaceId, normalizedWorkspaceId, ex);
        }
        return toResponseDto(refreshConnectivityForWorkspace(saved));
    }

    /**
     * Restricted update for WORKSPACE_ADMIN — can edit: rootUrl, sharedSpaceId, workspaceId,
     * workspaceShortcode, clientId, clientKey, and status (workspace visibility). Cannot change
     * the workspace title.
     */
    public WorkspaceResponseDto updateAsWorkspaceAdmin(UUID id, WorkspaceUpdateRequestDto request) {
        Workspace workspace = workspaceRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + id));

        String normalizedRootUrl = normalizeRootUrl(request.getRootUrl());
        String normalizedSharedSpaceId = request.getSharedSpaceId().trim();
        String normalizedWorkspaceId = request.getWorkspaceId().trim();
        rejectIfDuplicateCombination(normalizedRootUrl, normalizedSharedSpaceId, normalizedWorkspaceId, id);
        validateStatusChange(request.getStatus(), request.getWorkspaceShortcode());

        // WORKSPACE_ADMIN can only update these fields
        workspace.setRootUrl(normalizedRootUrl);
        workspace.setWorkspaceShortcode(request.getWorkspaceShortcode());
        workspace.setSharedSpaceId(normalizedSharedSpaceId);
        workspace.setWorkspaceId(normalizedWorkspaceId);
        workspace.setClientId(request.getClientId());
        // Visibility (status) may be changed by workspace admins for workspaces they administer
        workspace.setStatus(request.getStatus());

        // Only replace clientKey when the caller provides a real new value
        String newKey = request.getClientKey();
        if (newKey != null && !newKey.isBlank() && !CLIENT_KEY_PLACEHOLDER.equals(newKey)) {
            workspace.setClientKey(newKey);
        }

        // title is intentionally NOT updated — renaming a workspace remains a super admin operation
        Workspace saved;
        try {
            saved = workspaceRepository.save(workspace);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateExceptionFromRace(normalizedRootUrl, normalizedSharedSpaceId, normalizedWorkspaceId, ex);
        }
        return toResponseDto(refreshConnectivityForWorkspace(saved));
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

        try {
            ProbeResult probeResult = probeStories(rootUrl, sharedSpaceId, workspaceId, clientId, clientKey);
            if (request.getWorkspaceRecordId() != null) {
                updateConnectivityStatus(
                        request.getWorkspaceRecordId(),
                        WorkspaceConnectivityStatus.ONLINE,
                        probeResult.message()
                );
            }
            return WorkspaceConnectionTestResponseDto.builder()
                    .success(true)
                    .hasData(probeResult.hasData())
                    .workspaceId(workspaceId)
                    .message(probeResult.message())
                    .build();
        } catch (IllegalArgumentException ex) {
            if (request.getWorkspaceRecordId() != null) {
                updateConnectivityStatus(
                        request.getWorkspaceRecordId(),
                        WorkspaceConnectivityStatus.OFFLINE,
                        summarizeError(ex)
                );
            }
            throw ex;
        }
    }

    public void refreshConnectivityForAllWorkspaces() {
        List<Workspace> workspaces = workspaceRepository.findAll();
        for (Workspace workspace : workspaces) {
            refreshConnectivityForWorkspace(workspace);
        }
    }

    public void markWorkspaceOffline(UUID workspaceId, String message) {
        updateConnectivityStatus(workspaceId, WorkspaceConnectivityStatus.OFFLINE, message);
    }

    public void markWorkspaceOnline(UUID workspaceId, boolean hasData) {
        String message = hasData
                ? "Connection successful"
                : "Connection successful, but no data was returned from the server.";
        updateConnectivityStatus(workspaceId, WorkspaceConnectivityStatus.ONLINE, message);
    }

    private Workspace refreshConnectivityForWorkspace(Workspace workspace) {
        try {
            ProbeResult result = probeStories(
                    workspace.getRootUrl(),
                    workspace.getSharedSpaceId(),
                    workspace.getWorkspaceId(),
                    workspace.getClientId(),
                    workspace.getClientKey()
            );
            workspace.setConnectivityStatus(WorkspaceConnectivityStatus.ONLINE);
            workspace.setConnectivityCheckedAt(Instant.now());
            workspace.setConnectivityMessage(result.message());
        } catch (IllegalArgumentException ex) {
            workspace.setConnectivityStatus(WorkspaceConnectivityStatus.OFFLINE);
            workspace.setConnectivityCheckedAt(Instant.now());
            workspace.setConnectivityMessage(summarizeError(ex));
        }
        return workspaceRepository.save(workspace);
    }

    private ProbeResult probeStories(
            String rootUrl,
            String sharedSpaceId,
            String workspaceId,
            String clientId,
            String clientKey) {
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
            OctaneQueryLogger.log(log, "/stories", "-", List.of("id"));
            stories = octane.entityList("stories")
                    .get()
                    .addFields("id")
                    .limit(1)
                    .execute();
        } catch (RuntimeException ex) {
            log.error(
                    "Connection probe failed while fetching stories via SDK [rootUrl={}, sharedSpaceId={}, workspaceId={}, clientId={}]: {}",
                    rootUrl, sharedSpaceId, workspaceId, clientId, ex.getMessage(), ex);
            String details = ex.getMessage() == null || ex.getMessage().isBlank()
                    ? "Please check server URL and credentials."
                    : ex.getMessage();
            throw new IllegalArgumentException("Connection test failed: unable to fetch stories. " + details, ex);
        }

        if (stories == null || stories.isEmpty()) {
            log.warn("Connection probe passed but no stories were returned [workspaceId={}]", workspaceId);
            return new ProbeResult(false, "Connection successful, but no data was returned from the server.");
        }
        return new ProbeResult(true, "Connection successful");
    }

    private void updateConnectivityStatus(UUID workspaceId, WorkspaceConnectivityStatus status, String message) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("Workspace not found: " + workspaceId));
        workspace.setConnectivityStatus(status);
        workspace.setConnectivityCheckedAt(Instant.now());
        workspace.setConnectivityMessage(message);
        workspaceRepository.save(workspace);
    }

    private String summarizeError(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return "Connection failed.";
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
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

    /**
     * Trims the URL and strips any trailing slash(es) so that e.g. "https://ve.example.com/"
     * and "https://ve.example.com" are treated as the same Root URL for duplicate detection.
     */
    private String normalizeRootUrl(String rootUrl) {
        return UrlNormalizer.normalizeRootUrl(rootUrl);
    }

    /**
     * True when a workspace shortcode is missing/blank or is the literal placeholder "UNKNOWN"
     * (case-insensitive) — meaning the wizard could not confidently parse it from the ValueEdge
     * workspace name during discovery.
     */
    private boolean isShortcodeUnknown(String workspaceShortcode) {
        return workspaceShortcode == null
                || workspaceShortcode.isBlank()
                || UNKNOWN_SHORTCODE.equalsIgnoreCase(workspaceShortcode.trim());
    }

    /**
     * Determines the status to persist for a newly created workspace. Defaults to DRAFT when
     * no status is requested. A workspace whose shortcode is unknown can never be created as
     * ENABLED — the backend is the source of truth for this rule even though the wizard never
     * lets the user pick ENABLED in that case.
     */
    private WorkspaceStatus resolveStatusForCreate(WorkspaceStatus requestedStatus, String workspaceShortcode) {
        WorkspaceStatus status = requestedStatus != null ? requestedStatus : WorkspaceStatus.DRAFT;
        if (status == WorkspaceStatus.ENABLED && isShortcodeUnknown(workspaceShortcode)) {
            throw new IllegalArgumentException(SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE);
        }
        return status;
    }

    /**
     * Guards updates: a workspace whose shortcode is (or is being set to) unknown may never be
     * moved to ENABLED. The frontend disables the option, but the backend enforces it too.
     */
    private void validateStatusChange(WorkspaceStatus requestedStatus, String workspaceShortcode) {
        if (requestedStatus == WorkspaceStatus.ENABLED && isShortcodeUnknown(workspaceShortcode)) {
            throw new IllegalArgumentException(SHORTCODE_UNKNOWN_ENABLE_BLOCKED_MESSAGE);
        }
    }

    /**
     * Emails the configured Super Admin notification addresses (Admin Panel → Notifications →
     * "admin@company.com"-style system notification recipients — see
     * {@link NotificationPreferencesService#getAdminNotificationEmails()}) that a newly created
     * workspace has an unknown shortcode and needs manual review. Best-effort — failures are
     * logged by {@link EmailService} and never propagate back to the caller.
     */
    private void notifySuperAdminsOfDraftWorkspace(Workspace workspace) {
        List<String> superAdminEmails = notificationPreferencesService.getAdminNotificationEmails();
        emailService.sendWorkspaceDraftReviewNotificationToAdmins(
                workspace.getTitle(),
                workspace.getRootUrl(),
                workspace.getSharedSpaceId(),
                workspace.getWorkspaceId(),
                workspace.getCreatedBy(),
                workspace.getCreatedAt(),
                superAdminEmails);
    }

    /**
     * True when the given creator email belongs to a user who does NOT hold the Super Admin
     * (ADMIN) role — i.e. a Workspace Admin. Unknown/missing users are treated as non-admin so a
     * notification is still sent rather than silently swallowed.
     */
    private boolean isWorkspaceAdminCreator(String createdByEmail) {
        if (createdByEmail == null || createdByEmail.isBlank()) {
            return false;
        }
        return appUserRepository.findByEmail(createdByEmail)
                .map(user -> user.getRoles().stream()
                        .noneMatch(role -> SUPER_ADMIN_ROLE.equals(role.getRoleName())))
                .orElse(false);
    }

    /**
     * Emails the configured Super Admin notification addresses (same recipient list as
     * {@link #notifySuperAdminsOfDraftWorkspace}) that a Workspace Admin created a new workspace
     * (informational — no action required). Only sent when the shortcode was successfully
     * detected; when it is UNKNOWN, {@link #notifySuperAdminsOfDraftWorkspace} already covers the
     * same details plus a call to action, so the two notifications are mutually exclusive per
     * workspace creation.
     */
    private void notifySuperAdminsOfWorkspaceCreatedByWorkspaceAdmin(Workspace workspace) {
        List<String> superAdminEmails = notificationPreferencesService.getAdminNotificationEmails();
        emailService.sendWorkspaceCreatedNotificationToAdmins(
                workspace.getTitle(),
                workspace.getRootUrl(),
                workspace.getSharedSpaceId(),
                workspace.getWorkspaceId(),
                workspace.getCreatedBy(),
                workspace.getCreatedAt(),
                superAdminEmails);
    }

    /**
     * Throws {@link DuplicateWorkspaceException} if a workspace already exists with the exact
     * same (Root URL, Shared Space ID, Workspace ID) combination. Sharing only one or two of
     * the three fields with another workspace is allowed — only an exact match of all three is
     * rejected. When {@code excludeId} is provided (updates), a match on that same workspace is
     * not considered a duplicate.
     */
    private void rejectIfDuplicateCombination(
            String rootUrl, String sharedSpaceId, String workspaceId, UUID excludeId) {
        workspaceRepository
                .findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(rootUrl, sharedSpaceId, workspaceId)
                .filter(existing -> excludeId == null || !existing.getId().equals(excludeId))
                .ifPresent(existing -> {
                    throw new DuplicateWorkspaceException(existing);
                });
    }

    /**
     * Recovers from a {@link DataIntegrityViolationException} raised by the DB-level unique
     * constraint (a concurrent request won the race to insert/update first). Re-fetches the
     * now-existing workspace so the caller can still report which workspace it collided with.
     */
    private DuplicateWorkspaceException duplicateExceptionFromRace(
            String rootUrl, String sharedSpaceId, String workspaceId, DataIntegrityViolationException cause) {
        Workspace existing = workspaceRepository
                .findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(rootUrl, sharedSpaceId, workspaceId)
                .orElse(null);
        if (existing == null) {
            // Constraint violation wasn't the workspace uniqueness one after all — surface as-is.
            throw cause;
        }
        return new DuplicateWorkspaceException(existing);
    }

    private WorkspaceResponseDto toResponseDto(Workspace workspace) {
        return WorkspaceResponseDto.builder()
                .id(workspace.getId())
                .title(workspace.getTitle())
                .workspaceShortcode(workspace.getWorkspaceShortcode())
                .sharedSpaceId(workspace.getSharedSpaceId())
                .workspaceId(workspace.getWorkspaceId())
                .clientId(workspace.getClientId())
                // Never return the real clientKey
                .clientKey(CLIENT_KEY_PLACEHOLDER)
                .clientKeyConfigured(workspace.getClientKey() != null && !workspace.getClientKey().isBlank())
                .rootUrl(workspace.getRootUrl())
                .status(workspace.getStatus())
                .connectivityStatus(workspace.getConnectivityStatus())
                .connectivityCheckedAt(workspace.getConnectivityCheckedAt())
                .connectivityMessage(workspace.getConnectivityMessage())
                .build();
    }

    private record ProbeResult(boolean hasData, String message) {
    }
}
