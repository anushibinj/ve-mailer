package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.config.SecurityConfig;
import com.anushibinj.veemailer.dto.WorkspaceDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.service.AdminService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
public class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testUnauthorizedWithoutCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/admin/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new WorkspaceDto())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testUnauthorizedWithInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/admin/workspaces")
                .with(httpBasic("wrong", "credentials"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new WorkspaceDto())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateWorkspace() throws Exception {
        WorkspaceDto dto = new WorkspaceDto(null, "Test Workspace", "ss-1", "ws-1");
        Workspace workspace = Workspace.builder()
                .id(UUID.randomUUID())
                .title("Test Workspace")
                .sharedSpaceId("ss-1")
                .workspaceId("ws-1")
                .build();

        when(adminService.createWorkspace(any(WorkspaceDto.class))).thenReturn(workspace);

        mockMvc.perform(post("/api/v1/admin/workspaces")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Workspace"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateWorkspace() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceDto dto = new WorkspaceDto(id, "Updated Workspace", "ss-1", "ws-1");
        Workspace workspace = Workspace.builder()
                .id(id)
                .title("Updated Workspace")
                .sharedSpaceId("ss-1")
                .workspaceId("ws-1")
                .build();

        when(adminService.updateWorkspace(eq(id), any(WorkspaceDto.class))).thenReturn(workspace);

        mockMvc.perform(put("/api/v1/admin/workspaces/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Workspace"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteWorkspace() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(adminService).deleteWorkspace(id);

        mockMvc.perform(delete("/api/v1/admin/workspaces/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testCreateFilter() throws Exception {
        Filter dto = Filter.builder().title("Test Filter").description("Desc").query("Query").build();
        Filter filter = Filter.builder()
                .id(UUID.randomUUID())
                .title("Test Filter")
                .description("Desc")
                .query("Query")
                .build();

        when(adminService.createFilter(any(Filter.class))).thenReturn(filter);

        mockMvc.perform(post("/api/v1/admin/filters")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test Filter"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testUpdateFilter() throws Exception {
        UUID id = UUID.randomUUID();
        Filter dto = Filter.builder().id(id).title("Updated Filter").description("Desc").query("Query").build();
        Filter filter = Filter.builder()
                .id(id)
                .title("Updated Filter")
                .description("Desc")
                .query("Query")
                .build();

        when(adminService.updateFilter(eq(id), any(Filter.class))).thenReturn(filter);

        mockMvc.perform(put("/api/v1/admin/filters/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Filter"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testDeleteFilter() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(adminService).deleteFilter(id);

        mockMvc.perform(delete("/api/v1/admin/filters/{id}", id))
                .andExpect(status().isNoContent());
    }
}
