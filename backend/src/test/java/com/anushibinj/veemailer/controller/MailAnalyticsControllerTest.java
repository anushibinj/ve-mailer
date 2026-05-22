package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.service.AppUserDetailsService;
import com.anushibinj.veemailer.service.JwtService;
import com.anushibinj.veemailer.service.MailAnalyticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MailAnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class MailAnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MailAnalyticsService analyticsService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private JwtService jwtService;

    @Test
    void getSummary_returns200() throws Exception {
        when(analyticsService.getSummary(7)).thenReturn(Map.of(
                "mailsSentToday", 5L,
                "mailsSentPeriod", 42L,
                "uniqueRecipients", 10L,
                "activeWorkspaces", 3L,
                "topFilter", "Critical",
                "periodDays", 7
        ));

        mockMvc.perform(get("/api/admin/mail-analytics/summary").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mailsSentToday").value(5))
                .andExpect(jsonPath("$.topFilter").value("Critical"));
    }

    @Test
    void getDailyVolume_returns200() throws Exception {
        when(analyticsService.getDailyVolume(7)).thenReturn(
                List.of(Map.of("date", "2026-05-20", "count", 15L)));

        mockMvc.perform(get("/api/admin/mail-analytics/daily-volume").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").value("2026-05-20"))
                .andExpect(jsonPath("$[0].count").value(15));
    }

    @Test
    void getDailyRecipients_returns200() throws Exception {
        when(analyticsService.getDailyUniqueRecipients(7)).thenReturn(
                List.of(Map.of("date", "2026-05-21", "count", 3L)));

        mockMvc.perform(get("/api/admin/mail-analytics/daily-recipients").param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].count").value(3));
    }

    @Test
    void getWorkspaceDistribution_returns200() throws Exception {
        when(analyticsService.getWorkspaceDistribution(30)).thenReturn(
                List.of(Map.of("workspace", "Alpha", "count", 10L)));

        mockMvc.perform(get("/api/admin/mail-analytics/workspace-distribution").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].workspace").value("Alpha"));
    }

    @Test
    void getFilterUsage_returns200() throws Exception {
        when(analyticsService.getFilterUsage(30)).thenReturn(
                List.of(Map.of("filter", "Critical Defects", "count", 8L)));

        mockMvc.perform(get("/api/admin/mail-analytics/filter-usage").param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].filter").value("Critical Defects"));
    }

    @Test
    void getHistory_returns200WithPagination() throws Exception {
        MailAuditLog log = MailAuditLog.builder()
                .id(UUID.randomUUID())
                .recipientEmail("user@example.com")
                .deliveryStatus(DeliveryStatus.SUCCESS)
                .sentAt(Instant.parse("2026-05-20T10:00:00Z"))
                .ticketCount(5)
                .build();
        Page<MailAuditLog> page = new PageImpl<>(List.of(log));
        when(analyticsService.getHistory(any(), any(), any(), any(), any(), any(), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/mail-analytics/history")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].recipientEmail").value("user@example.com"))
                .andExpect(jsonPath("$.content[0].deliveryStatus").value("SUCCESS"));
    }

    @Test
    void getHistory_withStatusFilter() throws Exception {
        Page<MailAuditLog> page = new PageImpl<>(List.of());
        when(analyticsService.getHistory(any(), any(), any(), eq(DeliveryStatus.FAILED), any(), any(), eq(0), eq(20)))
                .thenReturn(page);

        mockMvc.perform(get("/api/admin/mail-analytics/history")
                        .param("status", "FAILED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty());
    }
}
