package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.WorkspaceDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.EmailSubscriberRepository;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private FilterRepository filterRepository;

    @Mock
    private EmailSubscriberRepository emailSubscriberRepository;

    @InjectMocks
    private AdminService adminService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCreateWorkspace() {
        WorkspaceDto dto = new WorkspaceDto(null, "Title", "ss", "ws");
        Workspace workspace = Workspace.builder().title("Title").build();
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(workspace);

        Workspace result = adminService.createWorkspace(dto);

        assertNotNull(result);
        assertEquals("Title", result.getTitle());
    }

    @Test
    void testUpdateWorkspace() {
        UUID id = UUID.randomUUID();
        WorkspaceDto dto = new WorkspaceDto(id, "Updated", "ss", "ws");
        Workspace existing = Workspace.builder().id(id).title("Old").build();
        when(workspaceRepository.findById(id)).thenReturn(Optional.of(existing));
        when(workspaceRepository.save(any(Workspace.class))).thenReturn(existing);

        Workspace result = adminService.updateWorkspace(id, dto);

        assertEquals("Updated", result.getTitle());
    }

    @Test
    void testDeleteWorkspace_Success() {
        UUID id = UUID.randomUUID();
        when(emailSubscriberRepository.existsByWorkspaceId(id)).thenReturn(false);

        adminService.deleteWorkspace(id);

        verify(workspaceRepository).deleteById(id);
    }

    @Test
    void testDeleteWorkspace_WithSubscribers() {
        UUID id = UUID.randomUUID();
        when(emailSubscriberRepository.existsByWorkspaceId(id)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> adminService.deleteWorkspace(id));
    }

    @Test
    void testCreateFilter() {
        Filter dto = Filter.builder().title("Title").build();
        Filter filter = Filter.builder().title("Title").build();
        when(filterRepository.save(any(Filter.class))).thenReturn(filter);

        Filter result = adminService.createFilter(dto);

        assertNotNull(result);
        assertEquals("Title", result.getTitle());
    }

    @Test
    void testUpdateFilter() {
        UUID id = UUID.randomUUID();
        Filter dto = Filter.builder().title("Updated").build();
        Filter existing = Filter.builder().id(id).title("Old").build();
        when(filterRepository.findById(id)).thenReturn(Optional.of(existing));
        when(filterRepository.save(any(Filter.class))).thenReturn(existing);

        Filter result = adminService.updateFilter(id, dto);

        assertEquals("Updated", result.getTitle());
    }

    @Test
    void testDeleteFilter_Success() {
        UUID id = UUID.randomUUID();
        when(emailSubscriberRepository.existsByFilterId(id)).thenReturn(false);

        adminService.deleteFilter(id);

        verify(filterRepository).deleteById(id);
    }

    @Test
    void testDeleteFilter_WithSubscribers() {
        UUID id = UUID.randomUUID();
        when(emailSubscriberRepository.existsByFilterId(id)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> adminService.deleteFilter(id));
    }
}
