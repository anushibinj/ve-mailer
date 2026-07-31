package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.AiPreferencesResponseDto;
import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.model.AppUser;
import com.anushibinj.veemailer.model.Filter;
import com.anushibinj.veemailer.model.Role;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.model.WorkspaceAdminMapping;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.repository.AppUserRepository;
import com.anushibinj.veemailer.repository.RoleRepository;
import com.anushibinj.veemailer.repository.WorkspaceAdminRepository;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    private FilterService filterService;

    @MockBean
    private AiPreferencesService aiPreferencesService;

    @MockBean
    private MailAnalyticsService mailAnalyticsService;

    // Real (non-mocked) repositories — used to seed data for WORKSPACE_ADMIN authorization tests,
    // since WorkspaceAdminService itself is not mocked in this full-context test.
    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private WorkspaceRepository workspaceRepository;

    @Autowired
    private WorkspaceAdminRepository workspaceAdminRepository;

    private static final UUID WORKSPACE_ID = UUID.randomUUID();
    private static final UUID FILTER_ID = UUID.randomUUID();
    private static final UUID SUB_ID = UUID.randomUUID();

    /**
     * Persists an AppUser with the WORKSPACE_ADMIN role for use in workspace-admin authorization
     * tests (WorkspaceAdminService reads real repositories, so a backing row is required).
     */
    private AppUser seedWorkspaceAdminUser(String email) {
        Role workspaceAdminRole = roleRepository.findByRoleName("WORKSPACE_ADMIN")
                .orElseGet(() -> roleRepository.save(Role.builder().roleName("WORKSPACE_ADMIN").build()));
        return appUserRepository.save(AppUser.builder()
                .name("Workspace Admin")
                .email(email)
                .passwordHash("n/a")
                .enabled(true)
                .roles(java.util.Set.of(workspaceAdminRole))
                .build());
    }

    private Workspace seedWorkspace(String title) {
        // Do not pre-assign the ID — the entity uses @GeneratedValue, so Hibernate assigns the
        // real identifier on save; callers must use the returned entity's getId().
        return workspaceRepository.save(Workspace.builder()
                .title(title)
                .workspaceShortcode("77BD")
                .sharedSpaceId("s1")
                .workspaceId("w1")
                .clientId("c1")
                .clientKey("secret")
                .rootUrl("https://ve.example.com")
                .status(WorkspaceStatus.DRAFT)
                .build());
    }

    private void seedWorkspaceAdminMapping(Workspace workspace, AppUser user) {
        workspaceAdminRepository.save(WorkspaceAdminMapping.builder()
                .workspace(workspace)
                .user(user)
                .createdAt(LocalDateTime.now())
                .createdBy(user.getEmail())
                .build());
    }

    // ── Workspace reads (MEMBER) ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canListWorkspaces() throws Exception {
        when(workspaceService.findAllForUser()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canGetWorkspaceById() throws Exception {
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(WORKSPACE_ID)
                .title("WS")
                .workspaceShortcode("77BD")
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
                        .content("{\"title\":\"x\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"s\",\"workspaceId\":\"w\",\"clientId\":\"c\",\"clientKey\":\"k\",\"rootUrl\":\"https://ve.example.com\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotUpdateWorkspace() throws Exception {
        mockMvc.perform(put("/api/v1/workspaces/{id}", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"s\",\"workspaceId\":\"w\",\"clientId\":\"c\",\"clientKey\":\"(unchanged)\",\"rootUrl\":\"https://ve.example.com\",\"status\":\"ENABLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_cannotDeleteWorkspace() throws Exception {
        mockMvc.perform(delete("/api/v1/workspaces/{id}", WORKSPACE_ID))
                .andExpect(status().isForbidden());
    }

    // ── WORKSPACE_ADMIN workspace creation & administration ──────────────────

    @Test
    @WithMockUser(username = "wsadmin-create@test.com", roles = "WORKSPACE_ADMIN")
    void workspaceAdmin_canCreateMultipleWorkspacesAndIsAutoAssignedAsAdmin() throws Exception {
        AppUser creator = seedWorkspaceAdminUser("wsadmin-create@test.com");
        UUID firstId = seedWorkspace("First WS").getId();
        UUID secondId = seedWorkspace("Second WS").getId();

        WorkspaceResponseDto firstDto = WorkspaceResponseDto.builder()
                .id(firstId).title("First WS").workspaceShortcode("77BD").sharedSpaceId("s1")
                .workspaceId("w1").clientId("c1").clientKey("(unchanged)").clientKeyConfigured(true).build();
        WorkspaceResponseDto secondDto = WorkspaceResponseDto.builder()
                .id(secondId).title("Second WS").workspaceShortcode("77BD").sharedSpaceId("s1")
                .workspaceId("w2").clientId("c1").clientKey("(unchanged)").clientKeyConfigured(true).build();
        when(workspaceService.create(any())).thenReturn(firstDto, secondDto);

        // WORKSPACE_ADMIN may create any number of workspaces — no limit enforced
        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"First WS\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"s1\",\"workspaceId\":\"w1\",\"clientId\":\"c1\",\"clientKey\":\"k\",\"rootUrl\":\"https://ve.example.com\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Second WS\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"s1\",\"workspaceId\":\"w2\",\"clientId\":\"c1\",\"clientKey\":\"k\",\"rootUrl\":\"https://ve.example.com\"}"))
                .andExpect(status().isCreated());

        // Creator is automatically assigned as workspace admin for each workspace created
        assertThat(workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(firstId, creator.getId())).isTrue();
        assertThat(workspaceAdminRepository.existsByWorkspace_IdAndUser_Id(secondId, creator.getId())).isTrue();
    }

    @Test
    @WithMockUser(username = "wsadmin-visibility@test.com", roles = "WORKSPACE_ADMIN")
    void workspaceAdmin_canChangeVisibilityOfOwnWorkspace() throws Exception {
        AppUser admin = seedWorkspaceAdminUser("wsadmin-visibility@test.com");
        Workspace ownWorkspace = seedWorkspace("Own WS");
        UUID ownWorkspaceId = ownWorkspace.getId();
        seedWorkspaceAdminMapping(ownWorkspace, admin);

        WorkspaceResponseDto updated = WorkspaceResponseDto.builder()
                .id(ownWorkspaceId).title("Own WS").workspaceShortcode("77BD").sharedSpaceId("s1")
                .workspaceId("w1").clientId("c1").clientKey("(unchanged)").clientKeyConfigured(true)
                .status(WorkspaceStatus.ENABLED).build();
        when(workspaceService.updateAsWorkspaceAdmin(eq(ownWorkspaceId), any())).thenReturn(updated);

        mockMvc.perform(put("/api/v1/workspaces/{id}", ownWorkspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Own WS\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"s1\",\"workspaceId\":\"w1\",\"clientId\":\"c1\",\"clientKey\":\"(unchanged)\",\"rootUrl\":\"https://ve.example.com\",\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "wsadmin-denied@test.com", roles = "WORKSPACE_ADMIN")
    void workspaceAdmin_cannotChangeSettingsForUnadministeredWorkspace() throws Exception {
        seedWorkspaceAdminUser("wsadmin-denied@test.com");
        UUID otherWorkspaceId = seedWorkspace("Someone Else's WS").getId();
        // Note: no WorkspaceAdminMapping seeded for this user/workspace pair

        mockMvc.perform(put("/api/v1/workspaces/{id}", otherWorkspaceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Someone Else's WS\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"s1\",\"workspaceId\":\"w1\",\"clientId\":\"c1\",\"clientKey\":\"(unchanged)\",\"rootUrl\":\"https://ve.example.com\",\"status\":\"ENABLED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "wsadmin-nodelete@test.com", roles = "WORKSPACE_ADMIN")
    void workspaceAdmin_cannotDeleteWorkspace() throws Exception {
        mockMvc.perform(delete("/api/v1/workspaces/{id}", WORKSPACE_ID))
                .andExpect(status().isForbidden());
    }

    // ── Filter reads (MEMBER) ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canListFilters() throws Exception {
        when(filterService.getAccessibleFilters(any(UUID.class), anyString(), anyBoolean())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/workspaces/{id}/filters", WORKSPACE_ID))
                .andExpect(status().isOk());
    }

    // ── Filter mutations (MEMBER own templates allowed) ─────────────────────

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canCreateFilter() throws Exception {
        Filter created = new Filter();
        created.setId(FILTER_ID);
        created.setTitle("f");
        created.setOwnerEmail("member@test.com");
        Workspace ws = new Workspace();
        ws.setId(WORKSPACE_ID);
        created.setWorkspace(ws);
        when(filterService.createFilter(any(), any())).thenReturn(created);

        mockMvc.perform(post("/api/v1/workspaces/{id}/filters", WORKSPACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"f\",\"entityType\":\"defect\",\"fields\":[\"id\"],\"criteria\":[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "member@test.com", roles = "MEMBER")
    void member_canUpdateOwnedFilter() throws Exception {
        Filter owned = new Filter();
        owned.setId(FILTER_ID);
        owned.setOwnerEmail("member@test.com");
        owned.setTitle("f");
        Workspace ws = new Workspace();
        ws.setId(WORKSPACE_ID);
        owned.setWorkspace(ws);
        when(filterService.getFilterInWorkspace(FILTER_ID, WORKSPACE_ID)).thenReturn(owned);
        when(filterService.updateFilter(any(UUID.class), any())).thenReturn(owned);

        mockMvc.perform(put("/api/v1/workspaces/{wid}/filters/{fid}", WORKSPACE_ID, FILTER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"f\",\"entityType\":\"defect\",\"fields\":[\"id\"],\"criteria\":[{\"field\":\"severity\",\"operator\":\"IN\",\"values\":[\"High\"]}]}"))
                .andExpect(status().isOk());
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
                .triageSlaThreshold(com.anushibinj.veemailer.model.TriageSlaThreshold.GREEN)
                .build();
        when(subscriptionService.createSubscription(anyString(), any(UUID.class), any(UUID.class), any(), any()))
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
                .triageSlaThreshold(com.anushibinj.veemailer.model.TriageSlaThreshold.GREEN)
                .build();
        when(subscriptionService.updateSubscription(anyString(), any(UUID.class), any(UUID.class), any(), any()))
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
        when(workspaceService.findAllForAdmin()).thenReturn(List.of());

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
                .id(WORKSPACE_ID).title("x").workspaceShortcode("77BD").sharedSpaceId("s").workspaceId("w")
                .clientId("c").clientKey("(unchanged)").clientKeyConfigured(true).build();
        when(workspaceService.create(any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"s\",\"workspaceId\":\"w\",\"clientId\":\"c\",\"clientKey\":\"k\",\"rootUrl\":\"https://ve.example.com\"}"))
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
        when(filterService.getAccessibleFilters(any(UUID.class), anyString(), anyBoolean())).thenReturn(List.of());

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
        when(filterService.createFilter(any(), any())).thenReturn(f);

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
