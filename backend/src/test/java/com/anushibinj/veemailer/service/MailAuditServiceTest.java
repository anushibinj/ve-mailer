package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailAuditServiceTest {

    @Mock
    private MailAuditLogRepository repository;

    @Mock
    private Clock clock;

    @InjectMocks
    private MailAuditService mailAuditService;

    private final Instant fixedNow = Instant.parse("2026-01-01T09:00:00Z");

    @Test
    void recordSuccess_savesEntryWithCorrectStatus() {
        UUID wsId = UUID.randomUUID();
        UUID filterId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        mailAuditService.recordSuccess(wsId, "My Workspace", "user@example.com",
                filterId, "Critical Defects", subId, null,
                "[ve-mailer] Critical Defects", 5, 120L);

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());

        MailAuditLog saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(saved.getWorkspaceId()).isEqualTo(wsId);
        assertThat(saved.getWorkspaceTitle()).isEqualTo("My Workspace");
        assertThat(saved.getRecipientEmail()).isEqualTo("user@example.com");
        assertThat(saved.getFilterTemplateId()).isEqualTo(filterId);
        assertThat(saved.getFilterTitle()).isEqualTo("Critical Defects");
        assertThat(saved.getSubscriptionId()).isEqualTo(subId);
        assertThat(saved.getMailSubject()).isEqualTo("[ve-mailer] Critical Defects");
        assertThat(saved.getTicketCount()).isEqualTo(5);
        assertThat(saved.getDurationMs()).isEqualTo(120L);
        assertThat(saved.getFailureReason()).isNull();
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    void recordFailure_savesEntryWithFailedStatus() {
        UUID wsId = UUID.randomUUID();

        mailAuditService.recordFailure(wsId, "Workspace A", "admin@test.com",
                null, "Filter X", null, null,
                "[ve-mailer] Filter X", 3, 50L, "Connection timeout");

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());

        MailAuditLog saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getFailureReason()).isEqualTo("Connection timeout");
    }

    @Test
    void recordFailure_truncatesLongFailureReason() {
        String longReason = "x".repeat(3000);

        mailAuditService.recordFailure(UUID.randomUUID(), "WS", "a@b.com",
                null, "F", null, null, "S", 0, 0L, longReason);

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());

        assertThat(captor.getValue().getFailureReason()).hasSize(2000);
    }

    @Test
    void recordSuccess_doesNotThrowWhenRepositoryFails() {
        when(repository.save(any())).thenThrow(new RuntimeException("DB error"));

        // Should not throw — error is logged internally
        mailAuditService.recordSuccess(UUID.randomUUID(), "WS", "a@b.com",
                null, "F", null, null, "S", 0, 0L);

        verify(repository).save(any());
    }

    @Test
    void recordSkippedNoTickets_savesSuccessEntryWithSkipReason() {
        UUID wsId = UUID.randomUUID();
        UUID filterId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        mailAuditService.recordSkippedNoTickets(
                wsId, "Workspace A", "user@example.com",
                filterId, "Open Defects", subId, null,
                "[ve-mailer] 0 tickets – Open Defects");

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());

        MailAuditLog saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(saved.getTicketCount()).isEqualTo(0);
        assertThat(saved.getDurationMs()).isEqualTo(0L);
        assertThat(saved.getFailureReason()).isEqualTo("Skipped sending email: no tickets matched the filter");
    }

    // ── Job-run-aware upserts ────────────────────────────────────────────────

    @Test
    void recordRetrying_firstAttempt_insertsNewRowWithSingularWording() {
        UUID jobRunId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        when(clock.instant()).thenReturn(fixedNow);
        when(repository.findByJobRunIdAndSubscriptionIdAndRecipientEmail(jobRunId, subId, "a@b.com"))
                .thenReturn(Optional.empty());

        mailAuditService.recordRetrying(jobRunId, UUID.randomUUID(), "WS", "a@b.com",
                UUID.randomUUID(), "Filter", subId, null, "Subject", 5, 1, 15);

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());
        MailAuditLog saved = captor.getValue();
        assertThat(saved.getJobRunId()).isEqualTo(jobRunId);
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.RETRYING);
        assertThat(saved.getFailureReason()).isEqualTo("Failed - Retried 1 time. Next retry in 15 minutes.");
        assertThat(saved.getSentAt()).isEqualTo(fixedNow);
    }

    @Test
    void recordRetrying_secondAttempt_updatesExistingRowWithPluralWording() {
        UUID jobRunId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        MailAuditLog existing = MailAuditLog.builder()
                .id(UUID.randomUUID()).jobRunId(jobRunId).subscriptionId(subId).recipientEmail("a@b.com")
                .deliveryStatus(DeliveryStatus.RETRYING).failureReason("Failed - Retried 1 time. Next retry in 15 minutes.")
                .build();
        when(clock.instant()).thenReturn(fixedNow);
        when(repository.findByJobRunIdAndSubscriptionIdAndRecipientEmail(jobRunId, subId, "a@b.com"))
                .thenReturn(Optional.of(existing));

        mailAuditService.recordRetrying(jobRunId, UUID.randomUUID(), "WS", "a@b.com",
                UUID.randomUUID(), "Filter", subId, null, "Subject", 5, 2, 15);

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(existing.getId());
        assertThat(captor.getValue().getFailureReason()).isEqualTo("Failed - Retried 2 times. Next retry in 15 minutes.");
    }

    @Test
    void recordSuccess_jobRunAware_updatesExistingRetryingRowInPlace() {
        UUID jobRunId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        MailAuditLog existing = MailAuditLog.builder()
                .id(UUID.randomUUID()).jobRunId(jobRunId).subscriptionId(subId).recipientEmail("a@b.com")
                .deliveryStatus(DeliveryStatus.RETRYING).build();
        when(clock.instant()).thenReturn(fixedNow);
        when(repository.findByJobRunIdAndSubscriptionIdAndRecipientEmail(jobRunId, subId, "a@b.com"))
                .thenReturn(Optional.of(existing));

        mailAuditService.recordSuccess(jobRunId, UUID.randomUUID(), "WS", "a@b.com",
                UUID.randomUUID(), "Filter", subId, null, "Subject", 5, 120L);

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());
        MailAuditLog saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(existing.getId());
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.SUCCESS);
        assertThat(saved.getFailureReason()).isNull();
        assertThat(saved.getDurationMs()).isEqualTo(120L);
    }

    @Test
    void recordFailure_jobRunAware_insertsWhenNoExistingRow() {
        UUID jobRunId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        when(clock.instant()).thenReturn(fixedNow);
        when(repository.findByJobRunIdAndSubscriptionIdAndRecipientEmail(any(), any(), any()))
                .thenReturn(Optional.empty());

        mailAuditService.recordFailure(jobRunId, UUID.randomUUID(), "WS", "a@b.com",
                UUID.randomUUID(), "Filter", subId, null, "Subject", 5, 10L, "boom");

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDeliveryStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(captor.getValue().getFailureReason()).isEqualTo("boom");
    }

    @Test
    void recordSuppressed_writesSkippedStatusWithGivenReason() {
        UUID jobRunId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        when(clock.instant()).thenReturn(fixedNow);
        when(repository.findByJobRunIdAndSubscriptionIdAndRecipientEmail(any(), any(), any()))
                .thenReturn(Optional.empty());

        mailAuditService.recordSuppressed(jobRunId, UUID.randomUUID(), "WS", "a@b.com",
                UUID.randomUUID(), "Filter", subId, null, "Subject",
                "Suppressed: a digest for this subscription was delivered 12 minutes ago.");

        ArgumentCaptor<MailAuditLog> captor = ArgumentCaptor.forClass(MailAuditLog.class);
        verify(repository).save(captor.capture());
        MailAuditLog saved = captor.getValue();
        assertThat(saved.getDeliveryStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(saved.getFailureReason())
                .isEqualTo("Suppressed: a digest for this subscription was delivered 12 minutes ago.");
    }

    @Test
    void markJobRunTerminal_flipsRetryingRowsForJobRun() {
        UUID jobRunId = UUID.randomUUID();

        mailAuditService.markJobRunTerminal(jobRunId, DeliveryStatus.FAILED, "Superseded by the next scheduled run");

        verify(repository).flipRetryingToTerminal(jobRunId, DeliveryStatus.FAILED, "Superseded by the next scheduled run");
    }

    @Test
    void markJobRunTerminal_nullJobRunId_doesNothing() {
        mailAuditService.markJobRunTerminal(null, DeliveryStatus.FAILED, "reason");

        verifyNoInteractions(repository);
    }
}
