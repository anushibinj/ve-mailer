package com.anushibinj.veemailer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mail_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private UUID workspaceId;

    private String workspaceTitle;

    @Column(nullable = false)
    private String recipientEmail;

    private UUID filterTemplateId;

    private String filterTitle;

    private UUID subscriptionId;

    private UUID userId;

    @Column(length = 500)
    private String mailSubject;

    @Column(nullable = false)
    private int ticketCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus deliveryStatus;

    @Column(length = 2000)
    private String failureReason;

    @Column(nullable = false)
    private Instant sentAt;

    private Long durationMs;
}
