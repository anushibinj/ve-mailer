package com.anushibinj.veemailer.controller;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.service.MailAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/mail-analytics")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class MailAnalyticsController {

    private final MailAnalyticsService analyticsService;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getSummary(days));
    }

    @GetMapping("/daily-volume")
    public ResponseEntity<List<Map<String, Object>>> getDailyVolume(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getDailyVolume(days));
    }

    @GetMapping("/daily-recipients")
    public ResponseEntity<List<Map<String, Object>>> getDailyRecipients(
            @RequestParam(defaultValue = "7") int days) {
        return ResponseEntity.ok(analyticsService.getDailyUniqueRecipients(days));
    }

    @GetMapping("/workspace-distribution")
    public ResponseEntity<List<Map<String, Object>>> getWorkspaceDistribution(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.getWorkspaceDistribution(days));
    }

    @GetMapping("/filter-usage")
    public ResponseEntity<List<Map<String, Object>>> getFilterUsage(
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(analyticsService.getFilterUsage(days));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<MailAuditLog>> getHistory(
            @RequestParam(required = false) UUID workspaceId,
            @RequestParam(required = false) String recipientEmail,
            @RequestParam(required = false) String filterTitle,
            @RequestParam(required = false) DeliveryStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(analyticsService.getHistory(
                workspaceId, recipientEmail, filterTitle, status, from, to, page, size));
    }
}
