package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.model.AiPreferences;
import com.anushibinj.veemailer.repository.AiPreferencesRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamicAiClientServiceTest {

    @Mock
    private AiPreferencesRepository repository;

    @InjectMocks
    private DynamicAiClientService service;

    private AiPreferences validPrefs;

    @BeforeEach
    void setUp() {
        validPrefs = AiPreferences.builder()
                .id(UUID.randomUUID())
                .apiKey("sk-test-key")
                .baseUrl("https://api.openai.com")
                .chatCompletionsPath("/v1/chat/completions")
                .model("gpt-4.1-mini")
                .build();
    }

    @Test
    void getEntity_ReturnsNullWhenNoPrefsConfigured() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        assertNull(service.getEntity());
    }

    @Test
    void getEntity_ReturnsEntityWhenConfigured() {
        when(repository.findAll()).thenReturn(List.of(validPrefs));

        AiPreferences result = service.getEntity();

        assertNotNull(result);
        assertEquals("sk-test-key", result.getApiKey());
        assertEquals("https://api.openai.com", result.getBaseUrl());
    }

    @Test
    void getChatClient_ThrowsWhenNotConfigured() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.getChatClient());
        assertTrue(ex.getMessage().contains("AI preferences are not configured"));
    }

    @Test
    void getChatClient_ReturnsNonNullClientWhenConfigured() {
        when(repository.findAll()).thenReturn(List.of(validPrefs));

        ChatClient client = service.getChatClient();

        assertNotNull(client);
    }
}
