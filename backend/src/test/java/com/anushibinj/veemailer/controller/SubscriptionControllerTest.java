package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.ScheduleDto;
import com.anushibinj.veemailer.dto.SubscriptionRequestDto;
import com.anushibinj.veemailer.dto.VerificationRequestDto;
import com.anushibinj.veemailer.model.ActionType;
import com.anushibinj.veemailer.model.ScheduleType;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import com.anushibinj.veemailer.service.SubscriptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false) // Ignore security for unit test
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SubscriptionService subscriptionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Autowired
    private ObjectMapper objectMapper;

    private SubscriptionRequestDto buildRequest() {
        SubscriptionRequestDto request = new SubscriptionRequestDto();
        request.setEmail("user@test.com");
        request.setActionType(ActionType.SUBSCRIBE);
        request.setWorkspaceId(UUID.randomUUID());
        request.setFilterId(UUID.randomUUID());
        request.setSchedule(ScheduleDto.builder()
                .type(ScheduleType.DAILY)
                .hours(List.of(9, 15))
                .build());
        return request;
    }

    @Test
    void testRequestSubscription_Success() throws Exception {
        doNothing().when(subscriptionService).requestSubscription(anyString(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/subscriptions/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isOk());
    }

    @Test
    void testRequestSubscription_DuplicateHours_Returns400() throws Exception {
        doThrow(new IllegalArgumentException("Duplicate hour in schedule: 9"))
                .when(subscriptionService).requestSubscription(anyString(), any(), any(), any(), any());

        mockMvc.perform(post("/api/v1/subscriptions/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildRequest())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testVerifySubscription_Success() throws Exception {
        VerificationRequestDto request = new VerificationRequestDto();
        request.setEmail("user@test.com");
        request.setOtp("123456");

        doNothing().when(subscriptionService).verifyAndExecute(anyString(), anyString());

        mockMvc.perform(post("/api/v1/subscriptions/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testVerifySubscription_InvalidOtp() throws Exception {
        VerificationRequestDto request = new VerificationRequestDto();
        request.setEmail("user@test.com");
        request.setOtp("wrong");

        doThrow(new IllegalArgumentException("Invalid OTP")).when(subscriptionService).verifyAndExecute(anyString(), anyString());

        mockMvc.perform(post("/api/v1/subscriptions/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized()); // 401 mapped from our controller
    }

    @Test
    void testVerifySubscription_GenericException_Returns400() throws Exception {
        VerificationRequestDto request = new VerificationRequestDto();
        request.setEmail("user@test.com");
        request.setOtp("123456");

        doThrow(new RuntimeException("Unexpected error"))
                .when(subscriptionService).verifyAndExecute(anyString(), anyString());

        mockMvc.perform(post("/api/v1/subscriptions/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest()); // 400 from generic catch
    }
}
