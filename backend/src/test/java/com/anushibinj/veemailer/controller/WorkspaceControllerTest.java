package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.model.Frequency;
import com.anushibinj.veemailer.model.Workspace;
import com.anushibinj.veemailer.repository.WorkspaceRepository;
import com.anushibinj.veemailer.service.SubscriptionService;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WorkspaceRepository workspaceRepository;

    @MockBean
    private SubscriptionService subscriptionService;

    @Test
    void testGetWorkspaces_ExcludesSensitiveInfo() throws Exception {
        Workspace ws = new Workspace();
        ws.setId(UUID.randomUUID());
        ws.setTitle("Test Workspace");
        ws.setSharedSpaceId("space-1");
        ws.setWorkspaceId("work-1");
        ws.setClientId("secret-client-id");
        ws.setClientKey("secret-client-key");

        when(workspaceRepository.findAll()).thenReturn(Arrays.asList(ws));

        mockMvc.perform(get("/api/v1/workspaces")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Test Workspace"))
                .andExpect(jsonPath("$[0].sharedSpaceId").value("space-1"))
                .andExpect(jsonPath("$[0].clientId").doesNotExist())
                .andExpect(jsonPath("$[0].clientKey").doesNotExist());
    }

    @Test
    void testGetSubscriptions_ReturnsCorrectDtos() throws Exception {
        UUID workspaceId = UUID.randomUUID();

        SubscriptionResponseDTO dto = new SubscriptionResponseDTO("user@test.com", "All Bugs", Frequency.DAILY);

        when(subscriptionService.getActiveSubscriptionsForWorkspace(any(UUID.class)))
                .thenReturn(Arrays.asList(dto));

        mockMvc.perform(get("/api/v1/workspaces/" + workspaceId + "/subscriptions")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].recipientEmail").value("user@test.com"))
                .andExpect(jsonPath("$[0].filterTitle").value("All Bugs"))
                .andExpect(jsonPath("$[0].frequency").value("DAILY"))
                .andExpect(jsonPath("$[0].id").doesNotExist()); // Ensuring no internal DB ID leak
    }
}
