package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.exception.DuplicateWorkspaceException;
import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.model.Role;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.hpe.adm.nga.sdk.Octane;
import com.hpe.adm.nga.sdk.entities.OctaneCollection;
import com.hpe.adm.nga.sdk.model.EntityModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private OctaneCacheService octaneCacheService;

    @Mock
    private EmailService emailService;

    @Mock
    private NotificationPreferencesService notificationPreferencesService;

    @InjectMocks
    private WorkspaceService workspaceService;

    private Workspace buildWorkspace(UUID id) {
        Workspace ws = new Workspace();
        ws.setId(id);
        ws.setTitle("My WS");
        ws.setWorkspaceShortcode("77BD");
        ws.setSharedSpaceId("sp-1");
        ws.setWorkspaceId("ws-1");
        ws.setClientId("cid-1");
        ws.setClientKey("real-secret");
        ws.setRootUrl("https://ve.example.com");
        ws.setStatus(WorkspaceStatus.ENABLED);
        return ws;
    }

    @Test
    void findAll_MasksClientKey() {
        UUID id = UUID.randomUUID();
        when(workspaceRepository.findAll()).thenReturn(List.of(buildWorkspace(id)));

        List<WorkspaceResponseDto> result = workspaceService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClientKey()).isEqualTo(WorkspaceService.CLIENT_KEY_PLACEHOLDER);
        assertThat(result.get(0).isClientKeyConfigured()).isTrue();
    }

    @Test
    void findById_NotFound_Throws() {
        UUID id = UUID.randomUUID();
        when(workspaceRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceService.findById(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Workspace not found");
    }

    @Test
    void create_SharesRootUrlAndSharedSpaceId_DifferentWorkspaceId_Allowed() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-2")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.save(any())).thenReturn(saved);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("T", "77BD", "sp-1", "ws-2", "cid", "key", "https://ve.example.com", null);

        assertThat(workspaceService.create(req, "creator@test.com")).isNotNull();
    }

    @Test
    void create_SharesRootUrl_DifferentSharedSpaceId_Allowed() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-2", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.save(any())).thenReturn(saved);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("T", "77BD", "sp-2", "ws-1", "cid", "key", "https://ve.example.com", null);

        assertThat(workspaceService.create(req, "creator@test.com")).isNotNull();
    }

    @Test
    void create_SharesSharedSpaceId_DifferentRootUrl_Allowed() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://another.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.save(any())).thenReturn(saved);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("T", "77BD", "sp-1", "ws-1", "cid", "key", "https://another.example.com", null);

        assertThat(workspaceService.create(req, "creator@test.com")).isNotNull();
    }

    @Test
    void create_NormalizesTrailingSlashInRootUrl_BeforeDuplicateCheck() {
        Workspace existing = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.of(existing));

        // Trailing slash + surrounding whitespace should normalize to the same root URL
        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("T", "77BD", "sp-1", "ws-1", "cid", "key", "  https://ve.example.com/  ", null);

        assertThatThrownBy(() -> workspaceService.create(req, "creator@test.com"))
                .isInstanceOf(DuplicateWorkspaceException.class);
    }

    @Test
    void create_DuplicateCombination_Throws() {
        Workspace existing = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.of(existing));

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("T", "77BD", "sp-1", "ws-1", "cid", "key", "https://ve.example.com", null);

        assertThatThrownBy(() -> workspaceService.create(req, "creator@test.com"))
                .isInstanceOf(DuplicateWorkspaceException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_SharesOnlyWorkspaceId_DifferentRootUrl_Allowed() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://other.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.save(any())).thenReturn(saved);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("T", "77BD", "sp-1", "ws-1", "cid", "key", "https://other.example.com", null);

        WorkspaceResponseDto result = workspaceService.create(req, "creator@test.com");

        assertThat(result).isNotNull();
    }

    @Test
    void create_Success_MasksClientKey() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.save(any())).thenReturn(saved);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "77BD", "sp-1", "ws-1", "cid-1", "real-secret", "https://ve.example.com", null);

        WorkspaceResponseDto result = workspaceService.create(req, "creator@test.com");

        assertThat(result.getClientKey()).isEqualTo(WorkspaceService.CLIENT_KEY_PLACEHOLDER);
        assertThat(result.isClientKeyConfigured()).isTrue();
        assertThat(result.getWorkspaceShortcode()).isEqualTo("77BD");
    }

    @Test
    void create_RaceCondition_DataIntegrityViolation_ThrowsDuplicateWithExistingWorkspace() {
        Workspace existing = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1"))
                .thenReturn(Optional.empty()) // pre-check passes
                .thenReturn(Optional.of(existing)); // re-check after race loss finds the winner
        when(workspaceRepository.save(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate key"));

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "77BD", "sp-1", "ws-1", "cid-1", "real-secret", "https://ve.example.com", null);

        assertThatThrownBy(() -> workspaceService.create(req, "creator@test.com"))
                .isInstanceOf(DuplicateWorkspaceException.class);
    }

    // --- Workspace creation wizard: duplicate pre-check (Step 1) ---

    @Test
    void checkDuplicate_NoExistingCombination_ReturnsNotDuplicate() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());

        var result = workspaceService.checkDuplicate("https://ve.example.com", "sp-1", "ws-1");

        assertThat(result.isDuplicate()).isFalse();
    }

    @Test
    void checkDuplicate_ExistingCombination_Throws() {
        Workspace existing = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> workspaceService.checkDuplicate("https://ve.example.com", "sp-1", "ws-1"))
                .isInstanceOf(DuplicateWorkspaceException.class);
    }

    // --- Workspace status auto-determination + Super Admin email notification ---

    @Test
    void create_ShortcodeKnown_AutoEnabled_NoEmailSent() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        saved.setWorkspaceShortcode("77BD");
        when(workspaceRepository.save(any())).thenReturn(saved);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "77BD", "sp-1", "ws-1", "cid-1", "real-secret",
                        "https://ve.example.com", null);

        WorkspaceResponseDto result = workspaceService.create(req, "creator@test.com");

        assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.ENABLED);
        org.mockito.Mockito.verifyNoInteractions(emailService);
        org.mockito.Mockito.verifyNoInteractions(notificationPreferencesService);
    }

    @Test
    void create_ShortcodeUnknown_AutoDraft_EmailSentToSuperAdmins() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        saved.setWorkspaceShortcode("UNKNOWN");
        saved.setStatus(WorkspaceStatus.DRAFT);
        saved.setCreatedBy("creator@test.com");
        saved.setCreatedAt(java.time.Instant.now());
        when(workspaceRepository.save(any())).thenReturn(saved);

        when(notificationPreferencesService.getAdminNotificationEmails())
                .thenReturn(List.of("admin1@test.com", "admin2@test.com"));

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "UNKNOWN", "sp-1", "ws-1", "cid-1", "real-secret",
                        "https://ve.example.com", null);

        WorkspaceResponseDto result = workspaceService.create(req, "creator@test.com");

        assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.DRAFT);
        verify(emailService).sendWorkspaceDraftReviewNotificationToAdmins(
                eq(saved.getTitle()), eq(saved.getRootUrl()), eq(saved.getSharedSpaceId()), eq(saved.getWorkspaceId()),
                eq("creator@test.com"), any(), eq(List.of("admin1@test.com", "admin2@test.com")));
    }

    // --- Super Admin "workspace created by a Workspace Admin" notification (TODO.md) ---

    @Test
    void create_ShortcodeKnown_CreatorIsWorkspaceAdmin_EmailSentToSuperAdmins() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        saved.setWorkspaceShortcode("77BD");
        saved.setCreatedBy("wsadmin@test.com");
        saved.setCreatedAt(java.time.Instant.now());
        when(workspaceRepository.save(any())).thenReturn(saved);

        Role workspaceAdminRole = Role.builder().roleName("WORKSPACE_ADMIN").build();
        AppUser creator = AppUser.builder().name("WS Admin").email("wsadmin@test.com")
                .passwordHash("x").roles(java.util.Set.of(workspaceAdminRole)).build();
        when(appUserRepository.findByEmail("wsadmin@test.com")).thenReturn(Optional.of(creator));

        when(notificationPreferencesService.getAdminNotificationEmails())
                .thenReturn(List.of("admin1@test.com"));

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "77BD", "sp-1", "ws-1", "cid-1", "real-secret",
                        "https://ve.example.com", null);

        WorkspaceResponseDto result = workspaceService.create(req, "wsadmin@test.com");

        assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.ENABLED);
        verify(emailService).sendWorkspaceCreatedNotificationToAdmins(
                eq(saved.getTitle()), eq(saved.getRootUrl()), eq(saved.getSharedSpaceId()), eq(saved.getWorkspaceId()),
                eq("wsadmin@test.com"), any(), eq(List.of("admin1@test.com")));
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.never())
                .sendWorkspaceDraftReviewNotificationToAdmins(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void create_ShortcodeKnown_CreatorIsSuperAdmin_NoEmailSent() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        saved.setWorkspaceShortcode("77BD");
        saved.setCreatedBy("superadmin@test.com");
        when(workspaceRepository.save(any())).thenReturn(saved);

        Role globalAdminRole = Role.builder().roleName("ADMIN").build();
        AppUser creator = AppUser.builder().name("Super Admin").email("superadmin@test.com")
                .passwordHash("x").roles(java.util.Set.of(globalAdminRole)).build();
        when(appUserRepository.findByEmail("superadmin@test.com")).thenReturn(Optional.of(creator));

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "77BD", "sp-1", "ws-1", "cid-1", "real-secret",
                        "https://ve.example.com", null);

        workspaceService.create(req, "superadmin@test.com");

        org.mockito.Mockito.verifyNoInteractions(emailService);
        org.mockito.Mockito.verifyNoInteractions(notificationPreferencesService);
    }

    @Test
    void create_ShortcodeUnknown_CreatorIsWorkspaceAdmin_OnlyDraftReviewEmailSent_NotBoth() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());
        Workspace saved = buildWorkspace(UUID.randomUUID());
        saved.setWorkspaceShortcode("UNKNOWN");
        saved.setStatus(WorkspaceStatus.DRAFT);
        saved.setCreatedBy("wsadmin@test.com");
        saved.setCreatedAt(java.time.Instant.now());
        when(workspaceRepository.save(any())).thenReturn(saved);

        AppUser admin1 = AppUser.builder().name("Admin One").email("admin1@test.com").passwordHash("x").build();
        when(notificationPreferencesService.getAdminNotificationEmails()).thenReturn(List.of(admin1.getEmail()));

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "UNKNOWN", "sp-1", "ws-1", "cid-1", "real-secret",
                        "https://ve.example.com", null);

        workspaceService.create(req, "wsadmin@test.com");

        verify(emailService).sendWorkspaceDraftReviewNotificationToAdmins(
                any(), any(), any(), any(), any(), any(), any());
        org.mockito.Mockito.verify(emailService, org.mockito.Mockito.never())
                .sendWorkspaceCreatedNotificationToAdmins(any(), any(), any(), any(), any(), any(), any());
        // isWorkspaceAdminCreator() must never even be consulted once the shortcode-unknown branch is taken
        org.mockito.Mockito.verify(appUserRepository, org.mockito.Mockito.never()).findByEmail(any());
    }

    @Test
    void create_EnabledRequestedWithUnknownShortcode_Throws() {
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.empty());

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "UNKNOWN", "sp-1", "ws-1", "cid-1", "real-secret",
                        "https://ve.example.com", WorkspaceStatus.ENABLED);

        assertThatThrownBy(() -> workspaceService.create(req, "creator@test.com"))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verify(workspaceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void update_EnabledRequestedWithUnknownShortcode_Throws() {
        UUID id = UUID.randomUUID();
        Workspace existing = buildWorkspace(id);
        existing.setWorkspaceShortcode("UNKNOWN");
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.of(existing));

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto("Updated", "UNKNOWN", "sp-1", "ws-1", "cid-1",
                        WorkspaceService.CLIENT_KEY_PLACEHOLDER, "https://ve.example.com", WorkspaceStatus.ENABLED);

        assertThatThrownBy(() -> workspaceService.update(id, req))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verify(workspaceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateAsWorkspaceAdmin_EnabledRequestedWithUnknownShortcode_Throws() {
        UUID id = UUID.randomUUID();
        Workspace existing = buildWorkspace(id);
        existing.setWorkspaceShortcode("UNKNOWN");
        existing.setStatus(WorkspaceStatus.DRAFT);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.findFirstByRootUrlAndSharedSpaceIdAndWorkspaceId(
                "https://ve.example.com", "sp-1", "ws-1")).thenReturn(Optional.of(existing));

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto(null, "UNKNOWN", "sp-1", "ws-1", "cid-1",
                        WorkspaceService.CLIENT_KEY_PLACEHOLDER, "https://ve.example.com", WorkspaceStatus.ENABLED);

        assertThatThrownBy(() -> workspaceService.updateAsWorkspaceAdmin(id, req))
                .isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verify(workspaceRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void update_PlaceholderClientKey_PreservesExisting() {
        UUID id = UUID.randomUUID();
        Workspace existing = buildWorkspace(id);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto("Updated", "77BD", "sp-1", "ws-1", "cid-1",
                        WorkspaceService.CLIENT_KEY_PLACEHOLDER, "https://ve.example.com", WorkspaceStatus.ENABLED);

        workspaceService.update(id, req);

        // clientKey must remain the original value
        assertThat(existing.getClientKey()).isEqualTo("real-secret");
    }

    @Test
    void update_NewClientKey_ReplacesExisting() {
        UUID id = UUID.randomUUID();
        Workspace existing = buildWorkspace(id);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto("Updated", "77BD", "sp-1", "ws-1", "cid-1", "new-secret", "https://ve.example.com", WorkspaceStatus.ENABLED);

        workspaceService.update(id, req);

        assertThat(existing.getClientKey()).isEqualTo("new-secret");
    }

    @Test
    void update_NullClientKey_PreservesExisting() {
        UUID id = UUID.randomUUID();
        Workspace existing = buildWorkspace(id);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto("Updated", "77BD", "sp-1", "ws-1", "cid-1", null, "https://ve.example.com", WorkspaceStatus.ENABLED);

        workspaceService.update(id, req);

        assertThat(existing.getClientKey()).isEqualTo("real-secret");
    }

    @Test
    void updateAsWorkspaceAdmin_ChangesStatus_ButNotTitle() {
        UUID id = UUID.randomUUID();
        Workspace existing = buildWorkspace(id);
        existing.setStatus(WorkspaceStatus.DRAFT);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto("Attempted Rename", "77BD", "sp-1", "ws-1", "cid-1",
                        WorkspaceService.CLIENT_KEY_PLACEHOLDER, "https://ve.example.com", WorkspaceStatus.ENABLED);

        WorkspaceResponseDto result = workspaceService.updateAsWorkspaceAdmin(id, req);

        // Visibility (status) change is allowed for workspace admins
        assertThat(existing.getStatus()).isEqualTo(WorkspaceStatus.ENABLED);
        assertThat(result.getStatus()).isEqualTo(WorkspaceStatus.ENABLED);
        // Title remains unchanged — workspace admins cannot rename a workspace
        assertThat(existing.getTitle()).isEqualTo("My WS");
    }

    @Test
    void updateAsWorkspaceAdmin_NotFound_Throws() {
        UUID id = UUID.randomUUID();
        when(workspaceRepository.findById(id)).thenReturn(Optional.empty());

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto("x", "77BD", "sp-1", "ws-1", "cid-1",
                        WorkspaceService.CLIENT_KEY_PLACEHOLDER, "https://ve.example.com", WorkspaceStatus.ENABLED);

        assertThatThrownBy(() -> workspaceService.updateAsWorkspaceAdmin(id, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Workspace not found");
    }

    @Test
    void delete_NotFound_Throws() {
        UUID id = UUID.randomUUID();
        when(workspaceRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> workspaceService.delete(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Workspace not found");
    }

    @Test
    void delete_Success() {
        UUID id = UUID.randomUUID();
        when(workspaceRepository.existsById(id)).thenReturn(true);

        workspaceService.delete(id);

        verify(workspaceRepository).deleteById(id);
    }

    @Test
    void testConnection_Success_ReturnsHasDataTrue() {
        UUID workspaceRecordId = UUID.randomUUID();
        Workspace workspace = buildWorkspace(workspaceRecordId);
        when(workspaceRepository.findById(workspaceRecordId)).thenReturn(Optional.of(workspace));

        Octane octane = mock(Octane.class, RETURNS_DEEP_STUBS);
        @SuppressWarnings("unchecked")
        OctaneCollection<EntityModel> resultCollection = (OctaneCollection<EntityModel>) mock(OctaneCollection.class);

        when(octaneCacheService.getOctaneClient(
                eq("https://ve.example.com"),
                eq("cid-1"),
                eq("real-secret"),
                eq(4001),
                eq(5015)))
                .thenReturn(octane);

        when(octane.entityList("stories")
                .get()
                .addFields("id")
                .limit(1)
                .execute())
                .thenReturn(resultCollection);

        when(resultCollection.isEmpty()).thenReturn(false);

        WorkspaceConnectionTestRequestDto request = new WorkspaceConnectionTestRequestDto(
                workspaceRecordId,
                "4001",
                "5015",
                "cid-1",
                WorkspaceService.CLIENT_KEY_PLACEHOLDER,
                "https://ve.example.com"
        );

        WorkspaceConnectionTestResponseDto response = workspaceService.testConnection(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isHasData()).isTrue();
        assertThat(response.getWorkspaceId()).isEqualTo("5015");
    }

    @Test
    void testConnection_NoStories_ReturnsSuccessWithWarning() {
        Octane octane = mock(Octane.class, RETURNS_DEEP_STUBS);
        @SuppressWarnings("unchecked")
        OctaneCollection<EntityModel> resultCollection = (OctaneCollection<EntityModel>) mock(OctaneCollection.class);

        when(octaneCacheService.getOctaneClient(
                eq("https://ve.example.com"),
                eq("cid-1"),
                eq("new-secret"),
                eq(4001),
                eq(5015)))
                .thenReturn(octane);

        when(octane.entityList("stories")
                .get()
                .addFields("id")
                .limit(1)
                .execute())
                .thenReturn(resultCollection);

        when(resultCollection.isEmpty()).thenReturn(true);

        WorkspaceConnectionTestRequestDto request = new WorkspaceConnectionTestRequestDto(
                null,
                "4001",
                "5015",
                "cid-1",
                "new-secret",
                "https://ve.example.com"
        );

        WorkspaceConnectionTestResponseDto response = workspaceService.testConnection(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isHasData()).isFalse();
        assertThat(response.getMessage()).contains("no data");
    }
}
