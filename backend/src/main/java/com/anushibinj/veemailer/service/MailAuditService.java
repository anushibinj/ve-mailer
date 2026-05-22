package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.DeliveryStatus;
import com.anushibinj.veemailer.model.MailAuditLog;
import com.anushibinj.veemailer.repository.MailAuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailAuditService {

    private final MailAuditLogRepository repository;

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
}
