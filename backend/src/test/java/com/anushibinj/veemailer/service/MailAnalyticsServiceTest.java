package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailAnalyticsServiceTest {

    @Mock
    private MailAuditLogRepository repository;

    @InjectMocks
    private MailAnalyticsService analyticsService;

    @Test
    void getSummary_returnsExpectedKeys() {
        when(repository.countBySentAtAfter(any(Instant.class))).thenReturn(10L);
        when(repository.countUniqueRecipientsSince(any(Instant.class))).thenReturn(5L);
        when(repository.countActiveWorkspacesSince(any(Instant.class))).thenReturn(2L);
        when(repository.findTopFilterSince(any(Instant.class))).thenReturn("Defects");

        Map<String, Object> summary = analyticsService.getSummary(7);

        assertThat(summary).containsKeys("mailsSentToday", "mailsSentPeriod",
                "uniqueRecipients", "activeWorkspaces", "topFilter", "periodDays");
        assertThat(summary.get("mailsSentPeriod")).isEqualTo(10L);
        assertThat(summary.get("uniqueRecipients")).isEqualTo(5L);
        assertThat(summary.get("activeWorkspaces")).isEqualTo(2L);
        assertThat(summary.get("topFilter")).isEqualTo("Defects");
        assertThat(summary.get("periodDays")).isEqualTo(7);
    }

    @Test
    void getDailyVolume_mapsRowsCorrectly() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{java.sql.Date.valueOf("2026-05-20"), 15L});
        rows.add(new Object[]{java.sql.Date.valueOf("2026-05-21"), 23L});
        when(repository.countDailyVolumeSince(any(Instant.class)))
                .thenReturn(rows);

        List<Map<String, Object>> result = analyticsService.getDailyVolume(7);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("date")).isEqualTo("2026-05-20");
        assertThat(result.get(0).get("count")).isEqualTo(15L);
        assertThat(result.get(1).get("count")).isEqualTo(23L);
    }

    @Test
    void getDailyUniqueRecipients_mapsRowsCorrectly() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{java.sql.Date.valueOf("2026-05-20"), 3L});
        when(repository.countDailyUniqueRecipientsSince(any(Instant.class)))
                .thenReturn(rows);

        List<Map<String, Object>> result = analyticsService.getDailyUniqueRecipients(7);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("count")).isEqualTo(3L);
    }

    @Test
    void getWorkspaceDistribution_mapsRowsCorrectly() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Alpha", 10L});
        rows.add(new Object[]{"Beta", 5L});
        when(repository.countByWorkspaceSince(any(Instant.class)))
                .thenReturn(rows);

        List<Map<String, Object>> result = analyticsService.getWorkspaceDistribution(30);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("workspace")).isEqualTo("Alpha");
        assertThat(result.get(0).get("count")).isEqualTo(10L);
    }

    @Test
    void getFilterUsage_mapsRowsCorrectly() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{"Critical", 8L});
        when(repository.countByFilterSince(any(Instant.class)))
                .thenReturn(rows);

        List<Map<String, Object>> result = analyticsService.getFilterUsage(30);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("filter")).isEqualTo("Critical");
        assertThat(result.get(0).get("count")).isEqualTo(8L);
    }

    @Test
    void getHistory_delegatesToRepository() {
        MailAuditLog log = MailAuditLog.builder()
                .id(UUID.randomUUID())
                .recipientEmail("u@t.com")
                .deliveryStatus(DeliveryStatus.SUCCESS)
                .sentAt(Instant.now())
                .ticketCount(3)
                .build();
        Page<MailAuditLog> page = new PageImpl<>(List.of(log));
        when(repository.findFiltered(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(page);

        Page<MailAuditLog> result = analyticsService.getHistory(
                null, null, null, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getRecipientEmail()).isEqualTo("u@t.com");
    }
}
