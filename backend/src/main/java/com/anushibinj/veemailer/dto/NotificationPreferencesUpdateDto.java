package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating/updating notification preferences.
 * When requiresAuth is false, username and password are ignored.
 * Password can be null/blank/"(unchanged)" to preserve the existing value on update.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferencesUpdateDto {

    @NotBlank(message = "SMTP host is required")
    private String host;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private int port;

    @NotBlank(message = "From address is required")
    private String fromAddress;

    private boolean requiresAuth;

    // Required only when requiresAuth=true; validated in the service layer
    private String username;

    // Optional on update — if null/blank/"(unchanged)", preserves existing password
    private String password;

    private boolean startTlsEnabled;

    /** Admin email addresses that receive system-level notifications (e.g., new user onboarded). May contain addresses not in the system. */
    private List<String> adminNotificationEmails;
}
