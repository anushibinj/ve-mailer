package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionCreateDto;
import com.anushibinj.veemailer.dto.SubscriptionResponseDTO;
import com.anushibinj.veemailer.dto.SubscriptionUpdateDto;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the subscription CRUD endpoints now hosted on WorkspaceController.
 * The old OTP-based /request and /verify endpoints have been removed.
 */
@WebMvcTest(WorkspaceController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private WorkspaceService workspaceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private final UUID workspaceId = UUID.randomUUID();
    private final UUID subscriptionId = UUID.randomUUID();
    private final UUID filterId = UUID.randomUUID();

    @Test
    @WithMockUser(username = "user@test.com")
    void testCreateSubscription_ReturnsCreated() throws Exception {
        SubscriptionCreateDto request = new SubscriptionCreateDto(
                filterId,
                ScheduleDto.builder().type(ScheduleType.DAILY).hours(List.of(9, 15)).build());

        SubscriptionResponseDTO dto = SubscriptionResponseDTO.builder()
                .id(UUID.randomUUID())
                .recipientEmail("user@test.com")
                .filterId(filterId)
                .filterTitle("All Bugs")
                .schedule(ScheduleDto.builder().type(ScheduleType.DAILY).hours(List.of(9, 15)).build())
                .build();

        when(subscriptionService.createSubscription(eq("user@test.com"), eq(workspaceId), any(), any()))
                .thenReturn(dto);

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientEmail").value("user@test.com"))
                .andExpect(jsonPath("$.filterTitle").value("All Bugs"));
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void testCreateSubscription_InvalidSchedule_Returns400() throws Exception {
        when(subscriptionService.createSubscription(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Schedule hours must not be empty"));

        SubscriptionCreateDto request = new SubscriptionCreateDto(
                filterId,
                ScheduleDto.builder().type(ScheduleType.DAILY).hours(List.of()).build());

        mockMvc.perform(post("/api/v1/workspaces/" + workspaceId + "/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void testUpdateSubscription_ReturnsOk() throws Exception {
        SubscriptionUpdateDto request = new SubscriptionUpdateDto(
                ScheduleDto.builder().type(ScheduleType.WEEKLY).hours(List.of(10)).build());

        SubscriptionResponseDTO dto = SubscriptionResponseDTO.builder()
                .id(subscriptionId)
                .recipientEmail("user@test.com")
                .filterId(filterId)
                .filterTitle("All Bugs")
                .schedule(ScheduleDto.builder().type(ScheduleType.WEEKLY).hours(List.of(10)).build())
                .build();

        when(subscriptionService.updateSubscription(eq("user@test.com"), eq(subscriptionId), eq(workspaceId), any()))
                .thenReturn(dto);

        mockMvc.perform(put("/api/v1/workspaces/" + workspaceId + "/subscriptions/" + subscriptionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.type").value("WEEKLY"));
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void testDeleteSubscription_ReturnsNoContent() throws Exception {
        doNothing().when(subscriptionService).deleteSubscription(any(), any(), any());

        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/subscriptions/" + subscriptionId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "user@test.com")
    void testDeleteSubscription_NotFound_Returns400() throws Exception {
        doThrow(new IllegalArgumentException("Subscription not found"))
                .when(subscriptionService).deleteSubscription(any(), any(), any());

        mockMvc.perform(delete("/api/v1/workspaces/" + workspaceId + "/subscriptions/" + subscriptionId))
                .andExpect(status().isBadRequest());
    }
}

