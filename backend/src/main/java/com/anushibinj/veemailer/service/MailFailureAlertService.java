package com.anushibinj.veemailer.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Collects subscriber-mail delivery failures and alerts the admins
 * ({@code adminNotificationEmails}) with ONE consolidated e-mail per flush window, so a
 * widespread outage does not spam them. Failures are queued immediately and flushed on a short
 * fixed delay, which bounds how long admins wait to learn something is wrong.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailFailureAlertService {

    /** One failed delivery attempt to a subscriber. */
    public record Failure(String workspaceTitle, String filterTitle, String recipientEmail,
                          String reason, Instant occurredAt) {}

    private final ConcurrentLinkedQueue<Failure> pending = new ConcurrentLinkedQueue<>();
    private final EmailService emailService;
    private final NotificationPreferencesService notificationPreferencesService;

    public void recordFailure(String workspaceTitle, String filterTitle, String recipientEmail, String reason) {
        pending.add(new Failure(workspaceTitle, filterTitle, recipientEmail, reason, Instant.now()));
    }

    @Scheduled(fixedDelayString = "${veemailer.alerts.mail-failure.flush-interval-ms:60000}")
    @PreDestroy
    public void flush() {
        List<Failure> batch = new ArrayList<>();
        Failure f;
        while ((f = pending.poll()) != null) {
            batch.add(f);
        }
        if (batch.isEmpty()) {
            return;
        }
        try {
            List<String> adminEmails = notificationPreferencesService.getAdminNotificationEmails();
            if (adminEmails.isEmpty()) {
                log.warn("{} mail delivery failure(s) occurred but no adminNotificationEmails are configured", batch.size());
                return;
            }
            emailService.sendMailFailureAlertToAdmins(batch, adminEmails);
        } catch (Exception e) {
            // Never re-queue: if SMTP is down the alert would fail forever and grow unbounded.
            log.error("Failed to send consolidated mail-failure alert for {} failure(s): {}", batch.size(), e.getMessage());
        }
    }
}
