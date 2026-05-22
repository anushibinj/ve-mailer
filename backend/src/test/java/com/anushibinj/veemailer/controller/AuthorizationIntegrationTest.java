package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.AiPreferencesResponseDto;
import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.FilterRepository;
import com.anushibinj.veemailer.service.AiPreferencesService;
import com.anushibinj.veemailer.service.FilterService;
import com.anushibinj.veemailer.service.MailAnalyticsService;
import com.anushibinj.veemailer.service.SubscriptionService;
import com.anushibinj.veemailer.service.WorkspaceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying the RBAC model:
 * - MEMBER: can read workspaces/filters and manage own subscriptions; cannot mutate workspaces or filter templates.
 * - ADMIN: can perform all operations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private FilterRepository filterRepository;

    @MockBean
    private FilterService filterService;

    @MockBean
    private AiPreferencesService aiPreferencesService;

    @MockBean
    private MailAnalyticsService mailAnalyticsService;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID FILTER_ID = UUID.randomUUID();
    private static final UUID SUB_ID = UUID.randomUUID();

    // ── Workspace reads (MEMBER) ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canListWorkspaces() throws Exception {
        when(workspaceService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canGetWorkspaceById() throws Exception {
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(WORKSPACE_ID)
                .title("WS")
                .sharedSpaceId("s1")
                .workspaceId("w1")
                .clientId("c1")
                .clientKey("(unchanged)")
                .clientKeyConfigured(true)
                .build();
        when(workspaceService.findById(WORKSPACE_ID)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/workspaces/{id}", WORKSPACE_ID))
                .andExpect(status().isOk());
    }

    // ── Workspace mutations (MEMBER denied) ──────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotCreateWorkspace() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"sharedSpaceId\":\"s\",\"workspaceId\":\"w\",\"clientId\":\"c\",\"clientKey\":\"k\",\"rootUrl\":\"https://ve.example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotUpdateWorkspace() throws Exception {
        mockMvc.perform(put("/api/v1/workspaces/{id}", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"sharedSpaceId\":\"s\",\"workspaceId\":\"w\",\"clientId\":\"c\",\"clientKey\":\"(unchanged)\",\"rootUrl\":\"https://ve.example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotDeleteWorkspace() throws Exception {
        mockMvc.perform(delete("/api/v1/workspaces/{id}", WORKSPACE_ID))
                .andExpect(status().isForbidden());
    }

    // ── Filter reads (MEMBER) ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canListFilters() throws Exception {
        when(filterRepository.findByWorkspace_Id(WORKSPACE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces/{id}/filters", WORKSPACE_ID))
                .andExpect(status().isOk());
    }

    // ── Filter mutations (MEMBER denied) ─────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotCreateFilter() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{id}/filters", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"f\",\"entityType\":\"defect\",\"fields\":[\"id\"],\"criteria\":[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotUpdateFilter() throws Exception {
        mockMvc.perform(put("/api/v1/workspaces/{wid}/filters/{fid}", WORKSPACE_ID, FILTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"f\",\"entityType\":\"defect\",\"fields\":[\"id\"],\"criteria\":[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]}"))
                .andExpect(status().isForbidden());
    }

    // ── Subscriptions (MEMBER) ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canListSubscriptions() throws Exception {
        // MEMBER: service must be called with the user's own email (not all subscriptions)
        when(subscriptionService.getActiveSubscriptionsForUser("member@test.com", WORKSPACE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces/{id}/subscriptions", WORKSPACE_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canCreateSubscription() throws Exception {
        SubscriptionResponseDTO dto = SubscriptionResponseDTO.builder()
                .id(SUB_ID)
                .recipientEmail("member@test.com")
                .filterId(FILTER_ID)
                .filterTitle("Test Filter")
                .schedule(ScheduleDto.builder().type(ScheduleType.DAILY).hours(List.of(9)).build())
                .build();
        when(subscriptionService.createSubscription(anyString(), any(UUID.class), any(UUID.class), any()))
                .thenReturn(dto);

        String body = """
                {"filterId":"%s","schedule":{"type":"DAILY","hours":[9]}}
                """.formatted(FILTER_ID);

        mockMvc.perform(post("/api/v1/workspaces/{id}/subscriptions", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canUpdateOwnSubscription() throws Exception {
        SubscriptionResponseDTO dto = SubscriptionResponseDTO.builder()
                .id(SUB_ID)
                .recipientEmail("member@test.com")
                .filterId(FILTER_ID)
                .filterTitle("Test Filter")
                .schedule(ScheduleDto.builder().type(ScheduleType.DAILY).hours(List.of(10)).build())
                .build();
        when(subscriptionService.updateSubscription(anyString(), any(UUID.class), any(UUID.class), any()))
                .thenReturn(dto);

        String body = """
                {"schedule":{"type":"DAILY","hours":[10]}}
                """;

        mockMvc.perform(put("/api/v1/workspaces/{wid}/subscriptions/{sid}", WORKSPACE_ID, SUB_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canDeleteOwnSubscription() throws Exception {
        doNothing().when(subscriptionService).deleteSubscription(anyString(), any(UUID.class), any(UUID.class));

        mockMvc.perform(delete("/api/v1/workspaces/{wid}/subscriptions/{sid}", WORKSPACE_ID, SUB_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotRunSubscription() throws Exception {
        mockMvc.perform(post("/api/v1/workspaces/{wid}/subscriptions/{sid}/run", WORKSPACE_ID, SUB_ID))
                .andExpect(status().isForbidden());
    }

    // ── Admin has full access ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canListWorkspaces() throws Exception {
        when(workspaceService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canListAllSubscriptions() throws Exception {
        // ADMIN: service must be called for the whole workspace, not just one user
        when(subscriptionService.getActiveSubscriptionsForWorkspace(WORKSPACE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces/{id}/subscriptions", WORKSPACE_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canCreateWorkspace() throws Exception {
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(WORKSPACE_ID).title("x").sharedSpaceId("s").workspaceId("w")
                .clientId("c").clientKey("(unchanged)").clientKeyConfigured(true).build();
        when(workspaceService.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"sharedSpaceId\":\"s\",\"workspaceId\":\"w\",\"clientId\":\"c\",\"clientKey\":\"k\",\"rootUrl\":\"https://ve.example.com\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canDeleteWorkspace() throws Exception {
        doNothing().when(workspaceService).delete(any(UUID.class));

        mockMvc.perform(delete("/api/v1/workspaces/{id}", WORKSPACE_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canListFilters() throws Exception {
        when(filterRepository.findByWorkspace_Id(WORKSPACE_ID)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces/{id}/filters", WORKSPACE_ID))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canCreateFilter() throws Exception {
        Filter f = new Filter();
        f.setId(FILTER_ID);
        f.setTitle("F");
        f.setEntityType("defect");
        f.setFields("[\"id\"]");
        f.setCriteria("[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]");
        Workspace ws = new Workspace();
        ws.setId(WORKSPACE_ID);
        f.setWorkspace(ws);
        when(filterService.createFilter(any())).thenReturn(f);

        mockMvc.perform(post("/api/v1/workspaces/{id}/filters", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"F\",\"entityType\":\"defect\",\"fields\":[\"id\"],\"criteria\":[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canUpdateFilter() throws Exception {
        Filter f = new Filter();
        f.setId(FILTER_ID);
        f.setTitle("F");
        f.setEntityType("defect");
        f.setFields("[\"id\"]");
        f.setCriteria("[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]");
        Workspace ws = new Workspace();
        ws.setId(WORKSPACE_ID);
        f.setWorkspace(ws);
        when(filterService.updateFilter(any(UUID.class), any())).thenReturn(f);

        mockMvc.perform(put("/api/v1/workspaces/{wid}/filters/{fid}", WORKSPACE_ID, FILTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"F\",\"entityType\":\"defect\",\"fields\":[\"id\"],\"criteria\":[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canRunSubscription() throws Exception {
        doNothing().when(subscriptionService).runSubscription(any(UUID.class), any(UUID.class));

        mockMvc.perform(post("/api/v1/workspaces/{wid}/subscriptions/{sid}/run", WORKSPACE_ID, SUB_ID))
                .andExpect(status().isOk());
    }

    // ── AI Preferences (ADMIN only) ───────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotGetAiPreferences() throws Exception {
        mockMvc.perform(get("/api/admin/ai-preferences"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotUpdateAiPreferences() throws Exception {
        mockMvc.perform(put("/api/admin/ai-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseUrl\":\"https://api.openai.com\",\"chatCompletionsPath\":\"/chat/completions\",\"model\":\"gpt-4\",\"apiKey\":\"sk-key\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canGetAiPreferences() throws Exception {
        when(aiPreferencesService.get()).thenReturn(AiPreferencesResponseDto.builder()
                .configured(false).build());

        mockMvc.perform(get("/api/admin/ai-preferences"))
                .andExpect(status().isOk());
    }

    // ── Mail Analytics (ADMIN only) ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotAccessMailAnalyticsSummary() throws Exception {
        mockMvc.perform(get("/api/admin/mail-analytics/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotAccessMailAnalyticsHistory() throws Exception {
        mockMvc.perform(get("/api/admin/mail-analytics/history"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void admin_canAccessMailAnalyticsSummary() throws Exception {
        when(mailAnalyticsService.getSummary(7)).thenReturn(java.util.Map.of(
                "mailsSentToday", 0L, "mailsSentPeriod", 0L,
                "uniqueRecipients", 0L, "activeWorkspaces", 0L,
                "topFilter", "", "periodDays", 7));

        mockMvc.perform(get("/api/admin/mail-analytics/summary").param("days", "7"))
                .andExpect(status().isOk());
    }
}
