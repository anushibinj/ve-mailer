package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MailAnalyticsService {

    private final MailAuditLogRepository repository;

    public Map<String, Object> getSummary(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        Instant todayStart = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("mailsSentToday", repository.countBySentAtAfter(todayStart));
        summary.put("mailsSentPeriod", repository.countBySentAtAfter(since));
        summary.put("uniqueRecipients", repository.countUniqueRecipientsSince(since));
        summary.put("activeWorkspaces", repository.countActiveWorkspacesSince(since));
        summary.put("topFilter", repository.findTopFilterSince(since));
        summary.put("periodDays", days);
        return summary;
    }

    public List<Map<String, Object>> getDailyVolume(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.countDailyVolumeSince(since).stream()
                .map(row -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("date", row[0].toString());
                    entry.put("count", ((Number) row[1]).longValue());
                    return entry;
                })
                .toList();
    }

    public List<Map<String, Object>> getDailyUniqueRecipients(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.countDailyUniqueRecipientsSince(since).stream()
                .map(row -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("date", row[0].toString());
                    entry.put("count", ((Number) row[1]).longValue());
                    return entry;
                })
                .toList();
    }

    public List<Map<String, Object>> getWorkspaceDistribution(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.countByWorkspaceSince(since).stream()
                .map(row -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("workspace", row[0]);
                    entry.put("count", ((Number) row[1]).longValue());
                    return entry;
                })
                .toList();
    }

    public List<Map<String, Object>> getFilterUsage(int days) {
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        return repository.countByFilterSince(since).stream()
                .map(row -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("filter", row[0]);
                    entry.put("count", ((Number) row[1]).longValue());
                    return entry;
                })
                .toList();
    }

    public Page<MailAuditLog> getHistory(UUID workspaceId, String recipientEmail,
                                         String filterTitle, DeliveryStatus status,
                                         Instant from, Instant to,
                                         int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));
        return repository.findFiltered(workspaceId, recipientEmail, filterTitle, status, from, to, pageable);
    }
}
