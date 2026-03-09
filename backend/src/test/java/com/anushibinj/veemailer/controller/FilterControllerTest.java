package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.service.FilterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
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
                  "criteria": [{"field":"severity","operator":"EQUAL_TO","negate":false,"values":["High"]}]
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
                  "criteria": [{"field":"severity","operator":"EQUAL_TO","negate":false,"values":["High"]}]
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
                  "criteria": [{"field":"severity","operator":"EQUAL_TO","negate":false,"values":["High"]}]
                }
                """.formatted(WORKSPACE_ID);

        mockMvc.perform(put("/api/v1/workspaces/{workspaceId}/filters/{filterId}", WORKSPACE_ID, filterId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated Title"));
    }
}
