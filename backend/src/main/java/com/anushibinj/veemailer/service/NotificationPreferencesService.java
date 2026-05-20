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
        List<NotificationPreferences> all = repository.findAll();
        NotificationPreferences prefs;

        if (all.isEmpty()) {
            // First-time setup — password is required
            if (dto.getPassword() == null || dto.getPassword().isBlank()
                    || PASSWORD_PLACEHOLDER.equals(dto.getPassword())) {
                throw new IllegalArgumentException("Password is required for initial configuration");
            }
            prefs = NotificationPreferences.builder()
                    .host(dto.getHost())
                    .port(dto.getPort())
                    .username(dto.getUsername())
                    .password(dto.getPassword())
                    .startTlsEnabled(dto.isStartTlsEnabled())
                    .build();
        } else {
            prefs = all.get(0);
            prefs.setHost(dto.getHost());
            prefs.setPort(dto.getPort());
            prefs.setUsername(dto.getUsername());
            prefs.setStartTlsEnabled(dto.isStartTlsEnabled());

            // Only replace password when the caller provides a real new value
            String newPassword = dto.getPassword();
            if (newPassword != null && !newPassword.isBlank()
                    && !PASSWORD_PLACEHOLDER.equals(newPassword)) {
                prefs.setPassword(newPassword);
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
                .username(prefs.getUsername())
                .password(PASSWORD_PLACEHOLDER)
                .startTlsEnabled(prefs.isStartTlsEnabled())
                .configured(true)
                .build();
    }
}
