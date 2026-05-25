package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.NotificationPreferencesResponseDto;
import com.anushibinj.veemailer.dto.NotificationPreferencesUpdateDto;
import com.anushibinj.veemailer.model.NotificationPreferences;
import com.anushibinj.veemailer.repository.NotificationPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
                    .build();
        } else {
            prefs = all.get(0);
            prefs.setHost(dto.getHost());
            prefs.setPort(dto.getPort());
            prefs.setFromAddress(dto.getFromAddress());
            prefs.setRequiresAuth(dto.isRequiresAuth());
            prefs.setStartTlsEnabled(dto.isStartTlsEnabled());

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
                .build();
    }
}
