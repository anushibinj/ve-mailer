package com.anushibinj.veemailer.controller;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.anushibinj.veemailer.dto.OctaneFieldValueDto;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import com.anushibinj.veemailer.service.OctaneMetadataService;
import com.anushibinj.veemailer.service.WorkspaceAdminService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OctaneMetadataController.class)
@AutoConfigureMockMvc(addFilters = false)
class OctaneMetadataControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OctaneMetadataService octaneMetadataService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private WorkspaceAdminService workspaceAdminService;

    @Test
    void testGetFieldValues_withSearchParam_delegatesSearchToService() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(octaneMetadataService.getFieldValues(eq(workspaceId), eq("owner"), eq("defect"), eq("john"), eq(null)))
                .thenReturn(List.of(OctaneFieldValueDto.builder().id("123").name("John Smith").build()));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/octane/field-values", workspaceId)
                        .param("fieldName", "owner")
                        .param("entityType", "defect")
                        .param("search", "john")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("123"))
                .andExpect(jsonPath("$[0].name").value("John Smith"));

        verify(octaneMetadataService).getFieldValues(workspaceId, "owner", "defect", "john", null);
    }

    @Test
    void testGetFieldValues_withIdsParam_delegatesIdsToService() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        when(octaneMetadataService.getFieldValues(eq(workspaceId), eq("owner"), eq("defect"), eq(null), eq("8666,1234")))
                .thenReturn(List.of(OctaneFieldValueDto.builder().id("8666").name("Anu Shibin Joseph Raj").build()));

        mockMvc.perform(get("/api/v1/workspaces/{workspaceId}/octane/field-values", workspaceId)
                        .param("fieldName", "owner")
                        .param("entityType", "defect")
                        .param("ids", "8666,1234")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("8666"))
                .andExpect(jsonPath("$[0].name").value("Anu Shibin Joseph Raj"));

        verify(octaneMetadataService).getFieldValues(workspaceId, "owner", "defect", null, "8666,1234");
    }
}
