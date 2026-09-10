package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailAuditService {

    private final MailAuditLogRepository repository;
    private final Clock clock;

    /**
     * Records a successful mail delivery.
     */
    @Async
    public void recordSuccess(UUID workspaceId, String workspaceTitle,
                              String recipientEmail, UUID filterTemplateId,
                              String filterTitle, UUID subscriptionId,
                              UUID userId, String mailSubject,
                              int ticketCount, long durationMs) {
        MailAuditLog entry = MailAuditLog.builder()
                .workspaceId(workspaceId)
                .workspaceTitle(workspaceTitle)
                .recipientEmail(recipientEmail)
                .filterTemplateId(filterTemplateId)
                .filterTitle(filterTitle)
                .subscriptionId(subscriptionId)
                .userId(userId)
                .mailSubject(mailSubject)
                .ticketCount(ticketCount)
                .deliveryStatus(DeliveryStatus.SUCCESS)
                .sentAt(Instant.now())
                .durationMs(durationMs)
                .build();
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist mail audit log (success) for {}", recipientEmail, e);
        }
    }

    /**
     * Records a failed mail delivery.
     */
    @Async
    public void recordFailure(UUID workspaceId, String workspaceTitle,
                              String recipientEmail, UUID filterTemplateId,
                              String filterTitle, UUID subscriptionId,
                              UUID userId, String mailSubject,
                              int ticketCount, long durationMs,
                              String failureReason) {
        MailAuditLog entry = MailAuditLog.builder()
                .workspaceId(workspaceId)
                .workspaceTitle(workspaceTitle)
                .recipientEmail(recipientEmail)
                .filterTemplateId(filterTemplateId)
                .filterTitle(filterTitle)
                .subscriptionId(subscriptionId)
                .userId(userId)
                .mailSubject(mailSubject)
                .ticketCount(ticketCount)
                .deliveryStatus(DeliveryStatus.FAILED)
                .failureReason(failureReason != null && failureReason.length() > 2000
                        ? failureReason.substring(0, 2000) : failureReason)
                .sentAt(Instant.now())
                .durationMs(durationMs)
                .build();
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist mail audit log (failure) for {}", recipientEmail, e);
        }
    }

    /**
     * Records a skipped notification when no tickets matched a filter.
     */
    @Async
    public void recordSkippedNoTickets(UUID workspaceId, String workspaceTitle,
                                       String recipientEmail, UUID filterTemplateId,
                                       String filterTitle, UUID subscriptionId,
                                       UUID userId, String mailSubject) {
        MailAuditLog entry = MailAuditLog.builder()
                .workspaceId(workspaceId)
                .workspaceTitle(workspaceTitle)
                .recipientEmail(recipientEmail)
                .filterTemplateId(filterTemplateId)
                .filterTitle(filterTitle)
                .subscriptionId(subscriptionId)
                .userId(userId)
                .mailSubject(mailSubject)
                .ticketCount(0)
                .deliveryStatus(DeliveryStatus.SKIPPED)
                .failureReason("Skipped sending email: no tickets matched the filter")
                .sentAt(Instant.now())
                .durationMs(0L)
                .build();
        try {
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist mail audit log (skipped) for {}", recipientEmail, e);
        }
    }

    // ── Job-run-aware upserts ────────────────────────────────────────────────
    //
    // One row per (jobRunId, subscriptionId, recipientEmail), updated in place across retry
    // attempts so row counts and existing analytics aggregates are unaffected. These must run
    // synchronously (no @Async) so a RETRYING row provably exists before the next attempt's
    // upsert runs against it — see the resilient-digest-jobs design doc.

    /** Upserts a RETRYING row recording how many attempts have failed so far. */
    @Transactional
    public void recordRetrying(UUID jobRunId, UUID workspaceId, String workspaceTitle,
                               String recipientEmail, UUID filterTemplateId, String filterTitle,
                               UUID subscriptionId, UUID userId, String mailSubject,
                               int ticketCount, int attemptsSoFar, int retryIntervalMinutes) {
        upsertJobRunRow(jobRunId, workspaceId, workspaceTitle, recipientEmail, filterTemplateId, filterTitle,
                subscriptionId, userId, mailSubject, ticketCount, DeliveryStatus.RETRYING,
                formatRetryingReason(attemptsSoFar, retryIntervalMinutes), 0L);
    }

    /** Job-run-aware overload of {@link #recordSuccess}: upserts rather than always inserting. */
    @Transactional
    public void recordSuccess(UUID jobRunId, UUID workspaceId, String workspaceTitle,
                              String recipientEmail, UUID filterTemplateId, String filterTitle,
                              UUID subscriptionId, UUID userId, String mailSubject,
                              int ticketCount, long durationMs) {
        upsertJobRunRow(jobRunId, workspaceId, workspaceTitle, recipientEmail, filterTemplateId, filterTitle,
                subscriptionId, userId, mailSubject, ticketCount, DeliveryStatus.SUCCESS, null, durationMs);
    }

    /** Job-run-aware overload of {@link #recordFailure}: upserts rather than always inserting. */
    @Transactional
    public void recordFailure(UUID jobRunId, UUID workspaceId, String workspaceTitle,
                              String recipientEmail, UUID filterTemplateId, String filterTitle,
                              UUID subscriptionId, UUID userId, String mailSubject,
                              int ticketCount, long durationMs, String failureReason) {
        upsertJobRunRow(jobRunId, workspaceId, workspaceTitle, recipientEmail, filterTemplateId, filterTitle,
                subscriptionId, userId, mailSubject, ticketCount, DeliveryStatus.FAILED,
                truncate(failureReason), durationMs);
    }

    /** Job-run-aware overload of {@link #recordSkippedNoTickets}: upserts rather than always inserting. */
    @Transactional
    public void recordSkippedNoTickets(UUID jobRunId, UUID workspaceId, String workspaceTitle,
                                       String recipientEmail, UUID filterTemplateId, String filterTitle,
                                       UUID subscriptionId, UUID userId, String mailSubject) {
        upsertJobRunRow(jobRunId, workspaceId, workspaceTitle, recipientEmail, filterTemplateId, filterTitle,
                subscriptionId, userId, mailSubject, 0, DeliveryStatus.SKIPPED,
                "Skipped sending email: no tickets matched the filter", 0L);
    }

    /**
     * Minimum-dispatch-gap / in-flight-run suppression: records a deliberate no-send, reusing
     * the SKIPPED status. {@code reason} is the caller's fully-formed message — the "delivered N
     * minutes ago" wording for the per-subscription recency check (B), or a distinct message for
     * the job-level in-flight check (A).
     */
    @Transactional
    public void recordSuppressed(UUID jobRunId, UUID workspaceId, String workspaceTitle,
                                 String recipientEmail, UUID filterTemplateId, String filterTitle,
                                 UUID subscriptionId, UUID userId, String mailSubject, String reason) {
        upsertJobRunRow(jobRunId, workspaceId, workspaceTitle, recipientEmail, filterTemplateId, filterTitle,
                subscriptionId, userId, mailSubject, 0, DeliveryStatus.SKIPPED, reason, 0L);
    }

    /** Flips every still-RETRYING row of a job run to a terminal status (superseded / max attempts reached). */
    @Transactional
    public void markJobRunTerminal(UUID jobRunId, DeliveryStatus status, String reason) {
        if (jobRunId == null) {
            return;
        }
        try {
            repository.flipRetryingToTerminal(jobRunId, status, truncate(reason));
        } catch (Exception e) {
            log.error("Failed to flip RETRYING audit rows to {} for job run {}", status, jobRunId, e);
        }
    }

    private void upsertJobRunRow(UUID jobRunId, UUID workspaceId, String workspaceTitle, String recipientEmail,
                                  UUID filterTemplateId, String filterTitle, UUID subscriptionId, UUID userId,
                                  String mailSubject, int ticketCount, DeliveryStatus status, String failureReason,
                                  long durationMs) {
        try {
            MailAuditLog entry = repository
                    .findByJobRunIdAndSubscriptionIdAndRecipientEmail(jobRunId, subscriptionId, recipientEmail)
                    .orElseGet(() -> MailAuditLog.builder()
                            .jobRunId(jobRunId)
                            .workspaceId(workspaceId)
                            .workspaceTitle(workspaceTitle)
                            .recipientEmail(recipientEmail)
                            .filterTemplateId(filterTemplateId)
                            .filterTitle(filterTitle)
                            .subscriptionId(subscriptionId)
                            .userId(userId)
                            .build());
            entry.setMailSubject(mailSubject);
            entry.setTicketCount(ticketCount);
            entry.setDeliveryStatus(status);
            entry.setFailureReason(failureReason);
            entry.setSentAt(clock.instant());
            entry.setDurationMs(durationMs);
            repository.save(entry);
        } catch (Exception e) {
            log.error("Failed to persist job-run mail audit log ({}) for {}", status, recipientEmail, e);
        }
    }

    static String formatRetryingReason(int attemptsSoFar, int retryIntervalMinutes) {
        String times = attemptsSoFar == 1 ? "time" : "times";
        return "Failed - Retried " + attemptsSoFar + " " + times + ". Next retry in " + retryIntervalMinutes + " minutes.";
    }

    private String truncate(String s) {
        return s != null && s.length() > 2000 ? s.substring(0, 2000) : s;
    }
}
