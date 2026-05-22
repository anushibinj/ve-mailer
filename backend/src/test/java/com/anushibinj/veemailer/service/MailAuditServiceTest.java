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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MailAuditServiceTest {

    @Mock
    private MailAuditLogRepository repository;

    @InjectMocks
    private MailAuditService mailAuditService;

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
}
