package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private String username;
    // Always "(unchanged)" — never the real password
    private String password;
    private boolean startTlsEnabled;
    private boolean configured;
}
