package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import com.anushibinj.veemailer.service.SubscriptionService;
import com.anushibinj.veemailer.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.security.test.context.support.WithMockUser;

@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void testGetWorkspaces_MasksClientKey() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id)
                .title("Test Workspace")
                .sharedSpaceId("space-1")
                .workspaceId("work-1")
                .clientId("my-client-id")
                .clientKey("(unchanged)")
                .clientKeyConfigured(true)
                .rootUrl("https://ve.example.com")
                .build();

        when(workspaceService.findAll()).thenReturn(Arrays.asList(dto));

        mockMvc.perform(get("/api/v1/workspaces").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Test Workspace"))
                .andExpect(jsonPath("$[0].sharedSpaceId").value("space-1"))
                .andExpect(jsonPath("$[0].clientId").value("my-client-id"))
                .andExpect(jsonPath("$[0].clientKey").value("(unchanged)"))
                .andExpect(jsonPath("$[0].clientKeyConfigured").value(true));
    }

    @Test
    void testCreateWorkspace_ReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceCreateRequestDto request =
                new WorkspaceCreateRequestDto("New WS", "sp-1", "ws-1", "cid-1", "secret-key", "https://ve.example.com");
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id)
                .title("New WS")
                .sharedSpaceId("sp-1")
                .workspaceId("ws-1")
                .clientId("cid-1")
                .clientKey("(unchanged)")
                .clientKeyConfigured(true)
                .rootUrl("https://ve.example.com")
                .build();

        when(workspaceService.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New WS"))
                .andExpect(jsonPath("$.clientKey").value("(unchanged)"))
                .andExpect(jsonPath("$.clientKeyConfigured").value(true));
    }

    @Test
    void testUpdateWorkspace_ReturnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id)
                .title("Updated WS")
                .sharedSpaceId("sp-1")
                .workspaceId("ws-1")
                .clientId("cid-1")
                .clientKey("(unchanged)")
                .clientKeyConfigured(true)
                .rootUrl("https://ve.example.com")
                .build();

        when(workspaceService.update(any(), any())).thenReturn(dto);

        String body = "{\"title\":\"Updated WS\",\"sharedSpaceId\":\"sp-1\","
                + "\"workspaceId\":\"ws-1\",\"clientId\":\"cid-1\",\"clientKey\":\"(unchanged)\","
                + "\"rootUrl\":\"https://ve.example.com\"}";

        mockMvc.perform(put("/api/v1/workspaces/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated WS"))
                .andExpect(jsonPath("$.clientKey").value("(unchanged)"));
    }

    @Test
    void testDeleteWorkspace_ReturnsNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(workspaceService).delete(any());

        mockMvc.perform(delete("/api/v1/workspaces/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetSubscriptions_Admin_ReturnsAllSubscriptions() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID filterId = UUID.randomUUID();
        SubscriptionResponseDTO dto = SubscriptionResponseDTO.builder()
                .id(subId)
                .recipientEmail("other@test.com")
                .filterId(filterId)
                .filterTitle("All Bugs")
                .schedule(ScheduleDto.builder()
                        .type(ScheduleType.DAILY)
                        .hours(List.of(9, 15))
                        .build())
                .build();

        when(subscriptionService.getActiveSubscriptionsForWorkspace(any(UUID.class)))
                .thenReturn(Arrays.asList(dto));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recipientEmail").value("other@test.com"))
                .andExpect(jsonPath("$[0].filterTitle").value("All Bugs"))
                .andExpect(jsonPath("$[0].schedule.type").value("DAILY"));
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void testGetSubscriptions_Member_ReturnsOnlyOwnSubscriptions() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        UUID filterId = UUID.randomUUID();
        SubscriptionResponseDTO dto = SubscriptionResponseDTO.builder()
                .id(subId)
                .recipientEmail("member@test.com")
                .filterId(filterId)
                .filterTitle("My Filter")
                .schedule(ScheduleDto.builder()
                        .type(ScheduleType.DAILY)
                        .hours(List.of(9))
                        .build())
                .build();

        when(subscriptionService.getActiveSubscriptionsForUser(anyString(), any(UUID.class)))
                .thenReturn(Arrays.asList(dto));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recipientEmail").value("member@test.com"))
                .andExpect(jsonPath("$[0].filterTitle").value("My Filter"));
    }
}

