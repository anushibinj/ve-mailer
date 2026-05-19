package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceUpdateRequestDto;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @InjectMocks
    private WorkspaceService workspaceService;

    private Workspace buildWorkspace(UUID id) {
        Workspace ws = new Workspace();
        ws.setId(id);
        ws.setTitle("My WS");
        ws.setSharedSpaceId("sp-1");
        ws.setWorkspaceId("ws-1");
        ws.setClientId("cid-1");
        ws.setClientKey("real-secret");
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
    void create_DuplicateWorkspaceId_Throws() {
        when(workspaceRepository.existsByWorkspaceId("ws-1")).thenReturn(true);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("T", "sp", "ws-1", "cid", "key");

        assertThatThrownBy(() -> workspaceService.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void create_Success_MasksClientKey() {
        when(workspaceRepository.existsByWorkspaceId("ws-1")).thenReturn(false);
        Workspace saved = buildWorkspace(UUID.randomUUID());
        when(workspaceRepository.save(any())).thenReturn(saved);

        WorkspaceCreateRequestDto req =
                new WorkspaceCreateRequestDto("My WS", "sp-1", "ws-1", "cid-1", "real-secret");

        WorkspaceResponseDto result = workspaceService.create(req);

        assertThat(result.getClientKey()).isEqualTo(WorkspaceService.CLIENT_KEY_PLACEHOLDER);
        assertThat(result.isClientKeyConfigured()).isTrue();
    }

    @Test
    void update_PlaceholderClientKey_PreservesExisting() {
        UUID id = UUID.randomUUID();
        Workspace existing = buildWorkspace(id);
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WorkspaceUpdateRequestDto req =
                new WorkspaceUpdateRequestDto("Updated", "sp-1", "ws-1", "cid-1",
                        WorkspaceService.CLIENT_KEY_PLACEHOLDER);

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
                new WorkspaceUpdateRequestDto("Updated", "sp-1", "ws-1", "cid-1", "new-secret");

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
                new WorkspaceUpdateRequestDto("Updated", "sp-1", "ws-1", "cid-1", null);

        workspaceService.update(id, req);

        assertThat(existing.getClientKey()).isEqualTo("real-secret");
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
}
