package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for notification preferences. The password is always masked.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferencesResponseDto {
    private String host;
    private int port;
    private String fromAddress;
    private boolean requiresAuth;
    private String username;
    // Always "(unchanged)" — never the real password
    private String password;
    private boolean startTlsEnabled;
    private boolean configured;
    /** Admin email addresses that receive system-level notifications (e.g., new user onboarded). */
    private List<String> adminNotificationEmails;
}
