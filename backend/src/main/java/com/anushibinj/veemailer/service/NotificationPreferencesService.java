package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.NotificationPreferencesResponseDto;
import com.anushibinj.veemailer.dto.NotificationPreferencesUpdateDto;
import com.anushibinj.veemailer.model.NotificationPreferences;
import com.anushibinj.veemailer.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationPreferencesService {

    static final String PASSWORD_PLACEHOLDER = "(unchanged)";

    private final NotificationPreferencesRepository repository;

    /**
     * Returns the current notification preferences (there is only one row).
     * If none exist yet, returns a response with configured=false.
     */
    public NotificationPreferencesResponseDto get() {
        List<NotificationPreferences> all = repository.findAll();
        if (all.isEmpty()) {
            return NotificationPreferencesResponseDto.builder()
                    .configured(false)
                    .adminNotificationEmails(Collections.emptyList())
                    .build();
        }
        NotificationPreferences prefs = all.get(0);
        return toResponseDto(prefs);
    }

    /**
     * Creates or updates the single notification preferences record.
     */
    public NotificationPreferencesResponseDto update(NotificationPreferencesUpdateDto dto) {
        if (dto.isRequiresAuth()) {
            if (dto.getUsername() == null || dto.getUsername().isBlank()) {
                throw new IllegalArgumentException("Username is required when authentication is enabled");
            }
        }

        List<NotificationPreferences> all = repository.findAll();
        NotificationPreferences prefs;

        if (all.isEmpty()) {
            if (dto.isRequiresAuth()
                    && (dto.getPassword() == null || dto.getPassword().isBlank()
                        || PASSWORD_PLACEHOLDER.equals(dto.getPassword()))) {
                throw new IllegalArgumentException("Password is required for initial configuration when authentication is enabled");
            }
            prefs = NotificationPreferences.builder()
                    .host(dto.getHost())
                    .port(dto.getPort())
                    .fromAddress(dto.getFromAddress())
                    .requiresAuth(dto.isRequiresAuth())
                    .username(dto.isRequiresAuth() ? dto.getUsername() : null)
                    .password(dto.isRequiresAuth() ? dto.getPassword() : null)
                    .startTlsEnabled(dto.isStartTlsEnabled())
                    .adminNotificationEmails(encodeEmails(dto.getAdminNotificationEmails()))
                    .build();
        } else {
            prefs = all.get(0);
            prefs.setHost(dto.getHost());
            prefs.setPort(dto.getPort());
            prefs.setFromAddress(dto.getFromAddress());
            prefs.setRequiresAuth(dto.isRequiresAuth());
            prefs.setStartTlsEnabled(dto.isStartTlsEnabled());
            prefs.setAdminNotificationEmails(encodeEmails(dto.getAdminNotificationEmails()));

            if (dto.isRequiresAuth()) {
                prefs.setUsername(dto.getUsername());
                String newPassword = dto.getPassword();
                if (newPassword != null && !newPassword.isBlank()
                        && !PASSWORD_PLACEHOLDER.equals(newPassword)) {
                    prefs.setPassword(newPassword);
                }
            } else {
                prefs.setUsername(null);
                prefs.setPassword(null);
            }
        }

        return toResponseDto(repository.save(prefs));
    }

    /**
     * Returns the raw entity for internal use (mail sender configuration).
     * Returns null if not yet configured.
     */
    public NotificationPreferences getEntity() {
        List<NotificationPreferences> all = repository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Returns the list of configured admin notification email addresses.
     * Returns an empty list if none are configured.
     */
    public List<String> getAdminNotificationEmails() {
        NotificationPreferences prefs = getEntity();
        if (prefs == null || prefs.getAdminNotificationEmails() == null || prefs.getAdminNotificationEmails().isBlank()) {
            return Collections.emptyList();
        }
        return decodeEmails(prefs.getAdminNotificationEmails());
    }

    private NotificationPreferencesResponseDto toResponseDto(NotificationPreferences prefs) {
        return NotificationPreferencesResponseDto.builder()
                .host(prefs.getHost())
                .port(prefs.getPort())
                .fromAddress(prefs.getFromAddress())
                .requiresAuth(prefs.isRequiresAuth())
                .username(prefs.getUsername())
                .password(prefs.isRequiresAuth() ? PASSWORD_PLACEHOLDER : null)
                .startTlsEnabled(prefs.isStartTlsEnabled())
                .configured(true)
                .adminNotificationEmails(decodeEmails(prefs.getAdminNotificationEmails()))
                .build();
    }

    /** Converts a list of emails to a comma-separated string for storage. */
    private static String encodeEmails(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return null;
        }
        return emails.stream()
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.joining(","));
    }

    /** Parses a comma-separated email string back into a list. */
    private static List<String> decodeEmails(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(e -> !e.isEmpty())
                .collect(Collectors.toList());
    }
}
