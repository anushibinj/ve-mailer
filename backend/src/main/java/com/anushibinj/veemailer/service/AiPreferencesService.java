package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.AiConnectionTestResultDto;
import com.anushibinj.veemailer.dto.AiPreferencesResponseDto;
import com.anushibinj.veemailer.dto.AiPreferencesUpdateDto;
import com.anushibinj.veemailer.model.AiPreferences;
import com.anushibinj.veemailer.repository.AiPreferencesRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiPreferencesService {

    static final String API_KEY_PLACEHOLDER = "(unchanged)";

    private final AiPreferencesRepository repository;
    private final DynamicAiClientService dynamicAiClientService;

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

    /**
     * Tests connectivity to the AI model described by the given (possibly unsaved) form
     * values, so admins can verify a connection before saving. If the API key is left as
     * the placeholder/blank, the currently saved API key is reused; if none has ever been
     * saved, a new API key must be provided.
     *
     * <p>Sends a simple "Hi" message with both a system prompt and a user prompt, then
     * returns the model's reply.
     *
     * @return a result DTO with success=true and the model reply, or success=false with an error message
     */
    public AiConnectionTestResultDto testConnection(AiPreferencesUpdateDto dto) {
        AiPreferences saved = getEntity();

        String apiKey = dto.getApiKey();
        if (apiKey == null || apiKey.isBlank() || API_KEY_PLACEHOLDER.equals(apiKey)) {
            if (saved == null) {
                return AiConnectionTestResultDto.builder()
                        .success(false)
                        .message("API key is required to test the connection.")
                        .build();
            }
            apiKey = saved.getApiKey();
        }

        AiPreferences candidate = AiPreferences.builder()
                .apiKey(apiKey)
                .baseUrl(dto.getBaseUrl())
                .chatCompletionsPath(dto.getChatCompletionsPath())
                .model(dto.getModel())
                .build();

        try {
            ChatClient chatClient = dynamicAiClientService.getChatClient(candidate);
            String reply = chatClient.prompt()
                    .system("You are a helpful assistant. Respond briefly and politely.")
                    .user("Hi")
                    .call()
                    .content();
            return AiConnectionTestResultDto.builder()
                    .success(true)
                    .message("Connection successful.")
                    .reply(reply != null ? reply.trim() : "")
                    .build();
        } catch (Exception e) {
            log.warn("AI connection test failed: {}", e.getMessage());
            return AiConnectionTestResultDto.builder()
                    .success(false)
                    .message("Connection failed: " + e.getMessage())
                    .build();
        }
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
