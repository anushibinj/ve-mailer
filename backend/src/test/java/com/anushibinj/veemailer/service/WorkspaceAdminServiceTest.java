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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceAdminServiceTest {

    @Mock
    private WorkspaceAdminRepository workspaceAdminRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private AppUserRepository appUserRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private EmailService emailService;

    private WorkspaceAdminService workspaceAdminService;

    private WorkspaceAdminService newService() {
        return new WorkspaceAdminService(workspaceAdminRepository, workspaceRepository, appUserRepository, roleRepository, emailService);
    }

    private Authentication authFor(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = List.of(roles).stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new UsernamePasswordAuthenticationToken(email, "n/a", authorities);
    }

    // ── autoAssignCreatorAsAdmin (workspace creation auto-assignment) ────────

    @Test
    void autoAssignCreatorAsAdmin_CreatesMapping_WhenNoneExists() {
        workspaceAdminService = newService();
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser creator = AppUser.builder().id(userId).name("WS Admin").email("wsadmin@test.com").build();
        Workspace workspace = Workspace.builder().id(workspaceId).title("New WS").build();

        when(appUserRepository.findByEmail("wsadmin@test.com")).thenReturn(Optional.of(creator));
        when(workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)).thenReturn(false);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        workspaceAdminService.autoAssignCreatorAsAdmin(workspaceId, "wsadmin@test.com");

        ArgumentCaptor<WorkspaceAdminMapping> captor = ArgumentCaptor.forClass(WorkspaceAdminMapping.class);
        verify(workspaceAdminRepository).save(captor.capture());
        assertThat(captor.getValue().getWorkspace()).isEqualTo(workspace);
        assertThat(captor.getValue().getUser()).isEqualTo(creator);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("wsadmin@test.com");
    }

    @Test
    void autoAssignCreatorAsAdmin_NoOp_WhenMappingAlreadyExists() {
        workspaceAdminService = newService();
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AppUser creator = AppUser.builder().id(userId).name("WS Admin").email("wsadmin@test.com").build();

        when(appUserRepository.findByEmail("wsadmin@test.com")).thenReturn(Optional.of(creator));
        when(workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(workspaceId, userId)).thenReturn(true);

        workspaceAdminService.autoAssignCreatorAsAdmin(workspaceId, "wsadmin@test.com");

        verify(workspaceAdminRepository, never()).save(any());
    }

    @Test
    void autoAssignCreatorAsAdmin_UserNotFound_Throws() {
        workspaceAdminService = newService();
        UUID workspaceId = UUID.randomUUID();
        when(appUserRepository.findByEmail("missing@test.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workspaceAdminService.autoAssignCreatorAsAdmin(workspaceId, "missing@test.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // ── canManageWorkspace (dual global-role + workspace-membership check) ──

    @Test
    void canManageWorkspace_GlobalAdmin_AlwaysTrue() {
        workspaceAdminService = newService();
        Authentication auth = authFor("admin@test.com", "ADMIN");

        assertThat(workspaceAdminService.canManageWorkspace(auth, UUID.randomUUID())).isTrue();
    }

    @Test
    void canManageWorkspace_WorkspaceAdmin_OnlyForAdministeredWorkspace() {
        workspaceAdminService = newService();
        UUID workspaceId = UUID.randomUUID();
        Authentication auth = authFor("wsadmin@test.com", "WORKSPACE_ADMIN");

        when(workspaceAdminRepository.existsByWorkspace_IdAndUser_Email(workspaceId, "wsadmin@test.com"))
                .thenReturn(true);

        assertThat(workspaceAdminService.canManageWorkspace(auth, workspaceId)).isTrue();
        assertThat(workspaceAdminService.canManageWorkspace(auth, UUID.randomUUID())).isFalse();
    }

    @Test
    void canManageWorkspace_PlainUser_AlwaysFalse() {
        workspaceAdminService = newService();
        Authentication auth = authFor("member@test.com", "MEMBER");

        assertThat(workspaceAdminService.canManageWorkspace(auth, UUID.randomUUID())).isFalse();
    }

    // ── assignWorkspaceAdmin restriction (regression coverage) ───────────────

    @Test
    void assignWorkspaceAdmin_WorkspaceAdminActor_PromotesPlainUserToWorkspaceAdmin() {
        workspaceAdminService = newService();
        UUID workspaceId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Workspace workspace = Workspace.builder().id(workspaceId).title("WS").build();
        AppUser target = AppUser.builder().id(targetUserId).name("Plain User").email("user@test.com")
                .roles(new java.util.HashSet<>(Set.of(Role.builder().roleName("MEMBER").build()))).build();
        Role workspaceAdminRole = Role.builder().id(UUID.randomUUID()).roleName("WORKSPACE_ADMIN").build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(appUserRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(workspaceId, targetUserId)).thenReturn(false);
        when(roleRepository.findByRoleName("WORKSPACE_ADMIN")).thenReturn(Optional.of(workspaceAdminRole));
        when(workspaceAdminRepository.save(any())).thenAnswer(inv -> {
            WorkspaceAdminMapping m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        WorkspaceAdminAssignRequestDto request = new WorkspaceAdminAssignRequestDto(targetUserId);

        WorkspaceAdminResponseDto result = workspaceAdminService.assignWorkspaceAdmin(
                workspaceId, request, "wsadmin@test.com");

        assertThat(result.getUserEmail()).isEqualTo("user@test.com");
        assertThat(target.getRoles()).extracting(Role::getRoleName).contains("WORKSPACE_ADMIN");
        verify(appUserRepository, times(1)).save(target);
        verify(emailService, times(1)).sendRoleChangeNotification("Plain User", "user@test.com", "MEMBER", "WORKSPACE_ADMIN", workspaceId.toString(), "WS");
    }

    @Test
    void assignWorkspaceAdmin_GlobalAdminActor_PromotesPlainUser() {
        workspaceAdminService = newService();
        UUID workspaceId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Workspace workspace = Workspace.builder().id(workspaceId).title("WS").build();
        AppUser target = AppUser.builder().id(targetUserId).name("Plain User").email("user@test.com")
                .roles(new java.util.HashSet<>(Set.of(Role.builder().roleName("MEMBER").build()))).build();
        Role workspaceAdminRole = Role.builder().id(UUID.randomUUID()).roleName("WORKSPACE_ADMIN").build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(appUserRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(workspaceId, targetUserId)).thenReturn(false);
        when(roleRepository.findByRoleName("WORKSPACE_ADMIN")).thenReturn(Optional.of(workspaceAdminRole));
        when(workspaceAdminRepository.save(any())).thenAnswer(inv -> {
            WorkspaceAdminMapping m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        WorkspaceAdminAssignRequestDto request = new WorkspaceAdminAssignRequestDto(targetUserId);
        WorkspaceAdminResponseDto result = workspaceAdminService.assignWorkspaceAdmin(
                workspaceId, request, "admin@test.com");

        assertThat(result.getUserEmail()).isEqualTo("user@test.com");
        assertThat(target.getRoles()).extracting(Role::getRoleName).contains("WORKSPACE_ADMIN");
        verify(appUserRepository, times(1)).save(target);
        verify(emailService, times(1)).sendRoleChangeNotification("Plain User", "user@test.com", "MEMBER", "WORKSPACE_ADMIN", workspaceId.toString(), "WS");
    }

    @Test
    void assignWorkspaceAdmin_TargetAlreadyWorkspaceAdmin_NoRoleChangeOrEmail() {
        workspaceAdminService = newService();
        UUID workspaceId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Workspace workspace = Workspace.builder().id(workspaceId).title("WS").build();
        AppUser target = AppUser.builder().id(targetUserId).name("Existing WS Admin").email("wsadmin2@test.com")
                .roles(new java.util.HashSet<>(Set.of(Role.builder().roleName("WORKSPACE_ADMIN").build()))).build();

        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(appUserRepository.findById(targetUserId)).thenReturn(Optional.of(target));
        when(workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(workspaceId, targetUserId)).thenReturn(false);
        when(workspaceAdminRepository.save(any())).thenAnswer(inv -> {
            WorkspaceAdminMapping m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        WorkspaceAdminAssignRequestDto request = new WorkspaceAdminAssignRequestDto(targetUserId);
        WorkspaceAdminResponseDto result = workspaceAdminService.assignWorkspaceAdmin(
                workspaceId, request, "wsadmin@test.com");

        assertThat(result.getUserEmail()).isEqualTo("wsadmin2@test.com");
        verify(appUserRepository, never()).save(any());
        verify(emailService, never()).sendRoleChangeNotification(any(), any(), any(), any(), any(), any());
    }
}
