package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.AiConnectionTestResultDto;
import com.anushibinj.veemailer.dto.AiPreferencesResponseDto;
import com.anushibinj.veemailer.dto.AiPreferencesUpdateDto;
import com.anushibinj.veemailer.model.AiPreferences;
import com.anushibinj.veemailer.repository.AiPreferencesRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiPreferencesServiceTest {

    @Mock
    private AiPreferencesRepository repository;

    @Mock
    private DynamicAiClientService dynamicAiClientService;

    @InjectMocks
    private AiPreferencesService service;

    @Test
    void get_WhenNoPreferencesExist_ReturnsNotConfigured() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        AiPreferencesResponseDto result = service.get();

        assertFalse(result.isConfigured());
        assertNull(result.getBaseUrl());
    }

    @Test
    void get_WhenPreferencesExist_ReturnsMaskedApiKey() {
        AiPreferences prefs = AiPreferences.builder()
                .id(UUID.randomUUID())
                .apiKey("sk-secret-key")
                .baseUrl("https://api.openai.com")
                .chatCompletionsPath("/chat/completions")
                .model("gpt-4.1-mini")
                .build();
        when(repository.findAll()).thenReturn(List.of(prefs));

        AiPreferencesResponseDto result = service.get();

        assertTrue(result.isConfigured());
        assertEquals("(unchanged)", result.getApiKey());
        assertEquals("https://api.openai.com", result.getBaseUrl());
        assertEquals("/chat/completions", result.getChatCompletionsPath());
        assertEquals("gpt-4.1-mini", result.getModel());
    }

    @Test
    void update_FirstTime_CreatesNewPreferences() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        when(repository.save(any())).thenAnswer(inv -> {
            AiPreferences saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("sk-new-key");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/chat/completions");
        dto.setModel("gpt-4.1-mini");

        AiPreferencesResponseDto result = service.update(dto);

        assertTrue(result.isConfigured());
        assertEquals("https://api.openai.com", result.getBaseUrl());
        assertEquals("(unchanged)", result.getApiKey());
        verify(repository).save(any());
    }

    @Test
    void update_FirstTime_ThrowsWhenApiKeyIsPlaceholder() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("(unchanged)");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/chat/completions");
        dto.setModel("gpt-4.1-mini");

        assertThrows(IllegalArgumentException.class, () -> service.update(dto));
    }

    @Test
    void update_FirstTime_ThrowsWhenApiKeyIsBlank() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/chat/completions");
        dto.setModel("gpt-4.1-mini");

        assertThrows(IllegalArgumentException.class, () -> service.update(dto));
    }

    @Test
    void update_Existing_PreservesApiKeyWhenPlaceholder() {
        AiPreferences existing = AiPreferences.builder()
                .id(UUID.randomUUID())
                .apiKey("existing-secret-key")
                .baseUrl("https://old.api.com")
                .chatCompletionsPath("/v1/chat/completions")
                .model("gpt-3.5-turbo")
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("(unchanged)");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/chat/completions");
        dto.setModel("gpt-4.1-mini");

        service.update(dto);

        assertEquals("existing-secret-key", existing.getApiKey());
        assertEquals("https://api.openai.com", existing.getBaseUrl());
        assertEquals("/chat/completions", existing.getChatCompletionsPath());
        assertEquals("gpt-4.1-mini", existing.getModel());
    }

    @Test
    void update_Existing_UpdatesApiKeyWhenNewValueProvided() {
        AiPreferences existing = AiPreferences.builder()
                .id(UUID.randomUUID())
                .apiKey("existing-secret-key")
                .baseUrl("https://api.openai.com")
                .chatCompletionsPath("/chat/completions")
                .model("gpt-4.1-mini")
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("brand-new-api-key");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/chat/completions");
        dto.setModel("gpt-4.1-mini");

        service.update(dto);

        assertEquals("brand-new-api-key", existing.getApiKey());
    }

    @Test
    void testConnection_WithUnsavedApiKey_UsesProvidedValuesAndReturnsReply() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(dynamicAiClientService.getChatClient(any(AiPreferences.class))).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Hello!");

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("sk-unsaved-key");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/v1/chat/completions");
        dto.setModel("gpt-4o");

        AiConnectionTestResultDto result = service.testConnection(dto);

        assertTrue(result.isSuccess());
        assertEquals("Hello!", result.getReply());

        ArgumentCaptor<AiPreferences> captor = ArgumentCaptor.forClass(AiPreferences.class);
        verify(dynamicAiClientService).getChatClient(captor.capture());
        assertEquals("sk-unsaved-key", captor.getValue().getApiKey());
        assertEquals("https://api.openai.com", captor.getValue().getBaseUrl());
    }

    @Test
    void testConnection_WithPlaceholderApiKey_FallsBackToSavedKey() {
        AiPreferences existing = AiPreferences.builder()
                .id(UUID.randomUUID())
                .apiKey("existing-secret-key")
                .baseUrl("https://old.api.com")
                .chatCompletionsPath("/old/path")
                .model("gpt-3.5-turbo")
                .build();
        when(repository.findAll()).thenReturn(List.of(existing));
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callResponseSpec = mock(ChatClient.CallResponseSpec.class);
        when(dynamicAiClientService.getChatClient(any(AiPreferences.class))).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Hi there");

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("(unchanged)");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/v1/chat/completions");
        dto.setModel("gpt-4o");

        AiConnectionTestResultDto result = service.testConnection(dto);

        assertTrue(result.isSuccess());
        ArgumentCaptor<AiPreferences> captor = ArgumentCaptor.forClass(AiPreferences.class);
        verify(dynamicAiClientService).getChatClient(captor.capture());
        assertEquals("existing-secret-key", captor.getValue().getApiKey());
        assertEquals("https://api.openai.com", captor.getValue().getBaseUrl());
    }

    @Test
    void testConnection_WithPlaceholderApiKeyAndNoSavedPreferences_ReturnsFailure() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("(unchanged)");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/v1/chat/completions");
        dto.setModel("gpt-4o");

        AiConnectionTestResultDto result = service.testConnection(dto);

        assertFalse(result.isSuccess());
        assertEquals("API key is required to test the connection.", result.getMessage());
        verify(dynamicAiClientService, never()).getChatClient(any(AiPreferences.class));
    }

    @Test
    void testConnection_WhenClientThrows_ReturnsFailure() {
        when(repository.findAll()).thenReturn(Collections.emptyList());
        when(dynamicAiClientService.getChatClient(any(AiPreferences.class)))
                .thenThrow(new RuntimeException("boom"));

        AiPreferencesUpdateDto dto = new AiPreferencesUpdateDto();
        dto.setApiKey("sk-unsaved-key");
        dto.setBaseUrl("https://api.openai.com");
        dto.setChatCompletionsPath("/v1/chat/completions");
        dto.setModel("gpt-4o");

        AiConnectionTestResultDto result = service.testConnection(dto);

        assertFalse(result.isSuccess());
        assertTrue(result.getMessage().contains("boom"));
    }

    @Test
    void getEntity_ReturnsNullWhenEmpty() {
        when(repository.findAll()).thenReturn(Collections.emptyList());

        assertNull(service.getEntity());
    }

    @Test
    void getEntity_ReturnsEntityWhenExists() {
        AiPreferences prefs = AiPreferences.builder()
                .id(UUID.randomUUID())
                .apiKey("sk-key")
                .baseUrl("https://api.openai.com")
                .chatCompletionsPath("/chat/completions")
                .model("gpt-4.1-mini")
                .build();
        when(repository.findAll()).thenReturn(List.of(prefs));

        AiPreferences result = service.getEntity();

        assertNotNull(result);
        assertEquals("sk-key", result.getApiKey());
    }
}
