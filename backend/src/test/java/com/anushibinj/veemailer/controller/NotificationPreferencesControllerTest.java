package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.dto.NotificationPreferencesResponseDto;
import com.anushibinj.veemailer.dto.NotificationPreferencesUpdateDto;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import com.anushibinj.veemailer.service.NotificationPreferencesService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationPreferencesController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationPreferencesControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationPreferencesService service;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    void get_ReturnsNotConfiguredWhenEmpty() throws Exception {
        NotificationPreferencesResponseDto response = NotificationPreferencesResponseDto.builder()
                .configured(false)
                .build();
        when(service.get()).thenReturn(response);

        mockMvc.perform(get("/api/admin/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    void get_ReturnsConfiguredPreferencesWithMaskedPassword() throws Exception {
        NotificationPreferencesResponseDto response = NotificationPreferencesResponseDto.builder()
                .host("smtp.example.com")
                .port(587)
                .username("admin@example.com")
                .password("(unchanged)")
                .startTlsEnabled(true)
                .configured(true)
                .build();
        when(service.get()).thenReturn(response);

        mockMvc.perform(get("/api/admin/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.host").value("smtp.example.com"))
                .andExpect(jsonPath("$.port").value(587))
                .andExpect(jsonPath("$.username").value("admin@example.com"))
                .andExpect(jsonPath("$.password").value("(unchanged)"))
                .andExpect(jsonPath("$.startTlsEnabled").value(true));
    }

    @Test
    void update_ReturnsUpdatedPreferences() throws Exception {
        NotificationPreferencesResponseDto response = NotificationPreferencesResponseDto.builder()
                .host("smtp.new.com")
                .port(465)
                .username("new@example.com")
                .password("(unchanged)")
                .startTlsEnabled(false)
                .configured(true)
                .build();
        when(service.update(any())).thenReturn(response);

        NotificationPreferencesUpdateDto request = new NotificationPreferencesUpdateDto();
        request.setHost("smtp.new.com");
        request.setPort(465);
        request.setUsername("new@example.com");
        request.setPassword("mypassword");
        request.setStartTlsEnabled(false);

        mockMvc.perform(put("/api/admin/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.host").value("smtp.new.com"))
                .andExpect(jsonPath("$.password").value("(unchanged)"));
    }

    @Test
    void update_ValidationFailsWhenHostBlank() throws Exception {
        NotificationPreferencesUpdateDto request = new NotificationPreferencesUpdateDto();
        request.setHost("");
        request.setPort(25);
        request.setUsername("user@test.com");
        request.setPassword("pass");
        request.setStartTlsEnabled(false);

        mockMvc.perform(put("/api/admin/notification-preferences")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
