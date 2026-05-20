package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.AiPreferencesResponseDto;
import com.anushibinj.veemailer.dto.AiPreferencesUpdateDto;
import com.anushibinj.veemailer.model.AiPreferences;
import com.anushibinj.veemailer.repository.AiPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiPreferencesService {

    static final String API_KEY_PLACEHOLDER = "(unchanged)";

    private final AiPreferencesRepository repository;

    /**
     * Returns the current AI preferences (there is only one row).
     * If none exist yet, returns a response with configured=false.
     */
    public AiPreferencesResponseDto get() {
        List<AiPreferences> all = repository.findAll();
        if (all.isEmpty()) {
            return AiPreferencesResponseDto.builder()
                    .configured(false)
                    .build();
        }
        return toResponseDto(all.get(0));
    }

    /**
     * Creates or updates the single AI preferences record.
     */
    public AiPreferencesResponseDto update(AiPreferencesUpdateDto dto) {
        List<AiPreferences> all = repository.findAll();
        AiPreferences prefs;

        if (all.isEmpty()) {
            // First-time setup — API key is required
            if (dto.getApiKey() == null || dto.getApiKey().isBlank()
                    || API_KEY_PLACEHOLDER.equals(dto.getApiKey())) {
                throw new IllegalArgumentException("API key is required for initial configuration");
            }
            prefs = AiPreferences.builder()
                    .apiKey(dto.getApiKey())
                    .baseUrl(dto.getBaseUrl())
                    .chatCompletionsPath(dto.getChatCompletionsPath())
                    .model(dto.getModel())
                    .build();
        } else {
            prefs = all.get(0);
            prefs.setBaseUrl(dto.getBaseUrl());
            prefs.setChatCompletionsPath(dto.getChatCompletionsPath());
            prefs.setModel(dto.getModel());

            // Only replace the API key when the caller provides a real new value
            String newApiKey = dto.getApiKey();
            if (newApiKey != null && !newApiKey.isBlank()
                    && !API_KEY_PLACEHOLDER.equals(newApiKey)) {
                prefs.setApiKey(newApiKey);
            }
        }

        return toResponseDto(repository.save(prefs));
    }

    /**
     * Returns the raw entity for internal use (AI client configuration).
     * Returns null if not yet configured.
     */
    public AiPreferences getEntity() {
        List<AiPreferences> all = repository.findAll();
        return all.isEmpty() ? null : all.get(0);
    }

    private AiPreferencesResponseDto toResponseDto(AiPreferences prefs) {
        return AiPreferencesResponseDto.builder()
                .apiKey(API_KEY_PLACEHOLDER)
                .baseUrl(prefs.getBaseUrl())
                .chatCompletionsPath(prefs.getChatCompletionsPath())
                .model(prefs.getModel())
                .configured(true)
                .build();
    }
}
