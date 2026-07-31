package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.WorkspaceConnectionTestResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceCreateRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceDiscoveryRequestDto;
import com.anushibinj.veemailer.dto.WorkspaceDiscoveryResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceDuplicateCheckResponseDto;
import com.anushibinj.veemailer.dto.WorkspaceResponseDto;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.model.Status;
import com.anushibinj.veemailer.model.WorkspaceStatus;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import com.anushibinj.veemailer.service.SubscriptionService;
import com.anushibinj.veemailer.service.UserQueryService;
import com.anushibinj.veemailer.service.WorkspaceAdminService;
import com.anushibinj.veemailer.service.WorkspaceDiscoveryService;
import com.anushibinj.veemailer.service.WorkspaceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    private WorkspaceDiscoveryService workspaceDiscoveryService;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private WorkspaceAdminService workspaceAdminService;

    @MockBean
    private UserQueryService userQueryService;

    @BeforeEach
    void setUp() {
        // Default: treat authenticated user as global admin for existing tests
        when(workspaceAdminService.isGlobalAdmin(any())).thenReturn(true);
        when(workspaceAdminService.canViewWorkspaceSubscriptions(any(), any())).thenReturn(true);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetWorkspaces_MasksClientKey() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id)
                .title("Test Workspace")
                .workspaceShortcode("77BD")
                .sharedSpaceId("space-1")
                .workspaceId("work-1")
                .clientId("my-client-id")
                .clientKey("(unchanged)")
                .clientKeyConfigured(true)
                .rootUrl("https://ve.example.com")
                .status(WorkspaceStatus.ENABLED)
                .build();

        when(workspaceService.findAllForAdmin()).thenReturn(Arrays.asList(dto));
        when(workspaceService.findAllForUser()).thenReturn(Arrays.asList(dto));

        mockMvc.perform(get("/api/v1/workspaces").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Test Workspace"))
                .andExpect(jsonPath("$[0].workspaceShortcode").value("77BD"))
                .andExpect(jsonPath("$[0].sharedSpaceId").value("space-1"))
                .andExpect(jsonPath("$[0].clientId").value("my-client-id"))
                .andExpect(jsonPath("$[0].clientKey").value("(unchanged)"))
                .andExpect(jsonPath("$[0].clientKeyConfigured").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testCreateWorkspace_ReturnsCreated() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceCreateRequestDto request =
                new WorkspaceCreateRequestDto("New WS", "77BD", "sp-1", "ws-1", "cid-1", "secret-key", "https://ve.example.com", null);
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id)
                .title("New WS")
                .workspaceShortcode("77BD")
                .sharedSpaceId("sp-1")
                .workspaceId("ws-1")
                .clientId("cid-1")
                .clientKey("(unchanged)")
                .clientKeyConfigured(true)
                .rootUrl("https://ve.example.com")
                .status(WorkspaceStatus.DRAFT)
                .build();

        when(workspaceService.create(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("New WS"))
                .andExpect(jsonPath("$.workspaceShortcode").value("77BD"))
                .andExpect(jsonPath("$.clientKey").value("(unchanged)"))
                .andExpect(jsonPath("$.clientKeyConfigured").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testCreateWorkspace_Duplicate_Returns409WithExistingWorkspaceDetails() throws Exception {
        UUID existingId = UUID.randomUUID();
        WorkspaceCreateRequestDto request =
                new WorkspaceCreateRequestDto("New WS", "77BD", "sp-1", "ws-1", "cid-1", "secret-key", "https://ve.example.com", null);

        com.anushibinj.veemailer.model.Workspace existing = new com.anushibinj.veemailer.model.Workspace();
        existing.setId(existingId);
        existing.setTitle("Finance Production");
        existing.setRootUrl("https://ve.example.com");
        existing.setSharedSpaceId("sp-1");
        existing.setWorkspaceId("ws-1");

        when(workspaceService.create(any(), any()))
                .thenThrow(new com.anushibinj.veemailer.exception.DuplicateWorkspaceException(existing));

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.existingWorkspace.id").value(existingId.toString()))
                .andExpect(jsonPath("$.existingWorkspace.name").value("Finance Production"))
                .andExpect(jsonPath("$.existingWorkspace.rootUrl").value("https://ve.example.com"))
                .andExpect(jsonPath("$.existingWorkspace.sharedSpaceId").value("sp-1"))
                .andExpect(jsonPath("$.existingWorkspace.workspaceId").value("ws-1"));
    }

    @Test
    @WithMockUser(username = "wsadmin@test.com", roles = "WORKSPACE_ADMIN")
    void testCreateWorkspace_WorkspaceAdmin_AutoAssignsCreatorAsAdmin() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceCreateRequestDto request =
                new WorkspaceCreateRequestDto("New WS", "77BD", "sp-1", "ws-1", "cid-1", "secret-key", "https://ve.example.com", null);
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id).title("New WS").workspaceShortcode("77BD").sharedSpaceId("sp-1").workspaceId("ws-1")
                .clientId("cid-1").clientKey("(unchanged)").clientKeyConfigured(true)
                .rootUrl("https://ve.example.com").status(WorkspaceStatus.DRAFT).build();

        when(workspaceAdminService.isGlobalAdmin(any())).thenReturn(false);
        when(workspaceAdminService.hasWorkspaceAdminRole(any())).thenReturn(true);
        when(workspaceService.create(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(workspaceAdminService).autoAssignCreatorAsAdmin(id, "wsadmin@test.com");
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testCreateWorkspace_GlobalAdmin_DoesNotAutoAssign() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceCreateRequestDto request =
                new WorkspaceCreateRequestDto("New WS", "77BD", "sp-1", "ws-1", "cid-1", "secret-key", "https://ve.example.com", null);
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id).title("New WS").workspaceShortcode("77BD").sharedSpaceId("sp-1").workspaceId("ws-1")
                .clientId("cid-1").clientKey("(unchanged)").clientKeyConfigured(true)
                .rootUrl("https://ve.example.com").status(WorkspaceStatus.DRAFT).build();

        when(workspaceService.create(any(), any())).thenReturn(dto);

        mockMvc.perform(post("/api/v1/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        verify(workspaceAdminService, never()).autoAssignCreatorAsAdmin(any(), any());
    }

    @Test
    @WithMockUser(username = "wsadmin@test.com", roles = "WORKSPACE_ADMIN")
    void testCreateWorkspace_WorkspaceAdmin_CanCreateMultipleWorkspaces() throws Exception {
        when(workspaceAdminService.isGlobalAdmin(any())).thenReturn(false);
        when(workspaceAdminService.hasWorkspaceAdminRole(any())).thenReturn(true);

        for (int i = 0; i < 3; i++) {
            UUID id = UUID.randomUUID();
            WorkspaceCreateRequestDto request =
                    new WorkspaceCreateRequestDto("WS " + i, "77BD", "sp-1", "ws-" + i, "cid-1", "secret-key", "https://ve.example.com", null);
            WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                    .id(id).title("WS " + i).workspaceShortcode("77BD").sharedSpaceId("sp-1").workspaceId("ws-" + i)
                    .clientId("cid-1").clientKey("(unchanged)").clientKeyConfigured(true)
                    .rootUrl("https://ve.example.com").status(WorkspaceStatus.DRAFT).build();
            when(workspaceService.create(any(), any())).thenReturn(dto);

            mockMvc.perform(post("/api/v1/workspaces")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        verify(workspaceService, times(3)).create(any(), any());
        verify(workspaceAdminService, times(3)).autoAssignCreatorAsAdmin(any(), any());
    }

    @Test
    void testUpdateWorkspace_ReturnsOk() throws Exception {
        UUID id = UUID.randomUUID();
        WorkspaceResponseDto dto = WorkspaceResponseDto.builder()
                .id(id)
                .title("Updated WS")
                .workspaceShortcode("77BD")
                .sharedSpaceId("sp-1")
                .workspaceId("ws-1")
                .clientId("cid-1")
                .clientKey("(unchanged)")
                .clientKeyConfigured(true)
                .rootUrl("https://ve.example.com")
                .status(WorkspaceStatus.ENABLED)
                .build();

        when(workspaceService.update(any(), any())).thenReturn(dto);

        String body = "{\"title\":\"Updated WS\",\"workspaceShortcode\":\"77BD\",\"sharedSpaceId\":\"sp-1\","
                + "\"workspaceId\":\"ws-1\",\"clientId\":\"cid-1\",\"clientKey\":\"(unchanged)\","
                + "\"rootUrl\":\"https://ve.example.com\",\"status\":\"ENABLED\"}";

        mockMvc.perform(put("/api/v1/workspaces/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated WS"))
                .andExpect(jsonPath("$.workspaceShortcode").value("77BD"))
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
    void testWorkspaceConnection_ReturnsOk() throws Exception {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceConnectionTestResponseDto responseDto = WorkspaceConnectionTestResponseDto.builder()
                .success(true)
                .hasData(true)
                .workspaceId("5015")
                .message("Connection successful")
                .build();

        when(workspaceAdminService.canManageWorkspace(any(), any(UUID.class))).thenReturn(true);
        when(workspaceService.testConnection(any())).thenReturn(responseDto);

        String requestBody = """
                {
                  "workspaceRecordId": "%s",
                  "sharedSpaceId": "4001",
                  "workspaceId": "5015",
                  "clientId": "cid-1",
                  "clientKey": "(unchanged)",
                  "rootUrl": "https://ve.example.com"
                }
                """.formatted(workspaceId);

        mockMvc.perform(post("/api/v1/workspaces/test-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.workspaceId").value("5015"));
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
                .triageSlaThreshold(com.anushibinj.veemailer.model.TriageSlaThreshold.GREEN)
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
        // Override default: member cannot view all subscriptions
        when(workspaceAdminService.canViewWorkspaceSubscriptions(any(), any())).thenReturn(false);

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
                .triageSlaThreshold(com.anushibinj.veemailer.model.TriageSlaThreshold.GREEN)
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

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testToggleSubscription_Admin_TogglesStatus() throws Exception {
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
                        .hours(List.of(9))
                        .build())
                .triageSlaThreshold(com.anushibinj.veemailer.model.TriageSlaThreshold.GREEN)
                .status(Status.DISABLED)
                .build();

        when(subscriptionService.toggleSubscriptionByAdmin(any(UUID.class), any(UUID.class)))
                .thenReturn(dto);

        mockMvc.perform(patch("/api/v1/workspaces/" + workspaceId + "/subscriptions/" + subId + "/toggle")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    // --- Workspace creation wizard: duplicate pre-check (Step 1) ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCheckDuplicateWorkspace_NoConflict_ReturnsOkFalse() throws Exception {
        when(workspaceService.checkDuplicate("https://ve.example.com", "sp-1", "ws-1"))
                .thenReturn(WorkspaceDuplicateCheckResponseDto.builder().duplicate(false).build());

        mockMvc.perform(get("/api/v1/workspaces/check-duplicate")
                        .param("rootUrl", "https://ve.example.com")
                        .param("sharedSpaceId", "sp-1")
                        .param("workspaceId", "ws-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCheckDuplicateWorkspace_Conflict_Returns409() throws Exception {
        com.anushibinj.veemailer.model.Workspace existing = new com.anushibinj.veemailer.model.Workspace();
        existing.setId(UUID.randomUUID());
        existing.setTitle("Finance Production");
        existing.setRootUrl("https://ve.example.com");
        existing.setSharedSpaceId("sp-1");
        existing.setWorkspaceId("ws-1");

        when(workspaceService.checkDuplicate("https://ve.example.com", "sp-1", "ws-1"))
                .thenThrow(new com.anushibinj.veemailer.exception.DuplicateWorkspaceException(existing));

        mockMvc.perform(get("/api/v1/workspaces/check-duplicate")
                        .param("rootUrl", "https://ve.example.com")
                        .param("sharedSpaceId", "sp-1")
                        .param("workspaceId", "ws-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.existingWorkspace.name").value("Finance Production"));
    }

    // --- Workspace creation wizard: metadata discovery (Step 2) ---

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDiscoverWorkspaceMetadata_ShortcodeDetected_ReturnsOk() throws Exception {
        WorkspaceDiscoveryRequestDto request = new WorkspaceDiscoveryRequestDto(
                "https://ot-internal.saas.microfocus.com", "4001", "5015", "cid-1", "secret");
        WorkspaceDiscoveryResponseDto responseDto = WorkspaceDiscoveryResponseDto.builder()
                .workspaceTitle("Portfolio-Hyd")
                .workspaceShortcode("77BD")
                .shortcodeDetected(true)
                .build();

        when(workspaceDiscoveryService.discover(any())).thenReturn(responseDto);

        mockMvc.perform(post("/api/v1/workspaces/discover-metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceTitle").value("Portfolio-Hyd"))
                .andExpect(jsonPath("$.workspaceShortcode").value("77BD"))
                .andExpect(jsonPath("$.shortcodeDetected").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDiscoverWorkspaceMetadata_NoMatch_Returns404WithRawResponse() throws Exception {
        WorkspaceDiscoveryRequestDto request = new WorkspaceDiscoveryRequestDto(
                "https://ot-internal.saas.microfocus.com", "4001", "5015", "cid-1", "secret");
        String rawJson = "{\"total_count\":1,\"data\":[{\"id\":\"5009\",\"name\":\"Other - AB12\"}]}";

        when(workspaceDiscoveryService.discover(any()))
                .thenThrow(new com.anushibinj.veemailer.exception.WorkspaceDiscoveryNotFoundException(
                        "No workspace with ID 5015 was found in the ValueEdge response.", rawJson));

        mockMvc.perform(post("/api/v1/workspaces/discover-metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No workspace with ID 5015 was found in the ValueEdge response."))
                .andExpect(jsonPath("$.rawResponse").value(rawJson));
    }
}

