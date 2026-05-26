package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.FilterDto;
import com.anushibinj.veemailer.dto.PreviewResponse;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FilterController.class)
@AutoConfigureMockMvc(addFilters = false)
class FilterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FilterRepository filterRepository;

    @MockBean
    private FilterService filterService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    private Filter buildTestFilter() {
        Workspace workspace = new Workspace();
        workspace.setId(WORKSPACE_ID);
        workspace.setTitle("Test Workspace");

        Filter f = new Filter();
        f.setId(UUID.randomUUID());
        f.setTitle("Urgent Tickets");
        f.setDescription("Show urgent");
        f.setWorkspace(workspace);
        f.setEntityType("defect");
        f.setFields("[\"id\",\"name\"]");
        f.setCriteria("[]");
        return f;
    }

    @Test
    void testGetFilters() throws Exception {
        Filter f = buildTestFilter();

        when(filterRepository.findByWorkspace_Id(WORKSPACE_ID)).thenReturn(Arrays.asList(f));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/filters", WORKSPACE_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Urgent Tickets"))
                .andExpect(jsonPath("$[0].entityType").value("defect"));
    }

    @Test
    void testCreateFilter() throws Exception {
        Filter saved = buildTestFilter();

        when(filterService.createFilter(any())).thenReturn(saved);

        String body = """
                {
                  "workspaceId": "%s",
                  "title": "Urgent Tickets",
                  "description": "Show urgent",
                  "entityType": "defect",
                  "fields": ["id", "name"],
                  "criteria": [{"field":"severity","operator":"IN","values":["High"]}]
                }
                """.formatted(WORKSPACE_ID);

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/filters", WORKSPACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Urgent Tickets"));
    }

    /**
     * Regression test: the frontend does NOT include workspaceId in the request body;
     * it is only present as a path variable. Previously, @NotNull on FilterDto.workspaceId
     * caused a 400 validation error before the controller could inject the path variable.
     */
    @Test
    void testCreateFilter_withoutWorkspaceIdInBody_usesPathVariable() throws Exception {
        Filter saved = buildTestFilter();

        when(filterService.createFilter(any())).thenReturn(saved);

        // Body intentionally omits workspaceId — mirrors what the frontend sends
        String body = """
                {
                  "title": "Urgent Tickets",
                  "description": "Show urgent",
                  "entityType": "defect",
                  "fields": ["id", "name"],
                  "criteria": [{"field":"severity","operator":"IN","values":["High"]}]
                }
                """;

        mockMvc.perform(post("/api/v1/workspaces/{workspaceId}/filters", WORKSPACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Urgent Tickets"));
    }

    @Test
    void testUpdateFilter() throws Exception {
        Filter updated = buildTestFilter();
        updated.setTitle("Updated Title");

        UUID filterId = updated.getId();
        when(filterService.updateFilter(any(), any())).thenReturn(updated);

        String body = """
                {
                  "workspaceId": "%s",
                  "title": "Updated Title",
                  "description": "Show urgent",
                  "entityType": "defect",
                  "fields": ["id", "name"],
                  "criteria": [{"field":"severity","operator":"IN","values":["High"]}]
                }
                """.formatted(WORKSPACE_ID);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/filters/{filterId}", WORKSPACE_ID, filterId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }

    @Test
    void testCloneFilter_returnsDto() throws Exception {
        UUID filterId = UUID.randomUUID();
        FilterDto cloned = FilterDto.builder()
                .title("Clone of Urgent Tickets")
                .description("Show urgent")
                .entityType("defect")
                .fields(java.util.List.of("id", "name"))
                .criteria(java.util.List.of())
                .build();

        when(filterService.cloneFilter(filterId)).thenReturn(cloned);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/filters/{filterId}/clone",
                        WORKSPACE_ID, filterId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Clone of Urgent Tickets"))
                .andExpect(jsonPath("$.entityType").value("defect"));
    }

    @Test
    void testDeleteFilter_returnsNoContent() throws Exception {
        UUID filterId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/workspaces/{workspaceId}/filters/{filterId}",
                        WORKSPACE_ID, filterId))
                .andExpect(status().isNoContent());
    }

    @Test
    void testPreviewFilter_returnsPreviewResponse() throws Exception {
        UUID filterId = UUID.randomUUID();
        PreviewResponse previewResponse = PreviewResponse.builder()
                .records(List.of(Map.of("id", "100", "name", "Test Bug")))
                .aiSummaryGenerated(false)
                .build();

        when(filterService.previewFilter(eq(filterId), eq(WORKSPACE_ID), eq(10)))
                .thenReturn(previewResponse);

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/filters/{filterId}/preview",
                        WORKSPACE_ID, filterId)
                        .param("limit", "10")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiSummaryGenerated").value(false))
                .andExpect(jsonPath("$.records", hasSize(1)))
                .andExpect(jsonPath("$.records[0].id").value("100"))
                .andExpect(jsonPath("$.records[0].name").value("Test Bug"));
    }
}
