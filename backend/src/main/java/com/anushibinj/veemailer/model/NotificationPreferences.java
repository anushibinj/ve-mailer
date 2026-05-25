package com.anushibinj.veemailer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Stores SMTP / notification channel configuration in the database.
 * Currently supports email (SMTP) configuration. Architecture allows
 * future extension to other channels (Slack, Teams, etc.).
 */
@Entity
@Table(name = "notification_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String host;

    @Column(nullable = false)
    private int port;

    @Column
    private String username;

    @Column
    private String password;

    @Column(nullable = false)
    private boolean startTlsEnabled;

    /** Email address placed in the From: header. */
    @Column
    private String fromAddress;

    /** When false, connects unauthenticated (port-25 relay). */
    @Builder.Default
    @Column(nullable = false)
    private boolean requiresAuth = true;
}
