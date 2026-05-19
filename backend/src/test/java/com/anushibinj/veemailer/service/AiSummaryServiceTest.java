package com.anushibinj.veemailer.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    private AiSummaryService aiSummaryService;

    @BeforeEach
    void setUp() {
        when(chatClientBuilder.build()).thenReturn(chatClient);
        aiSummaryService = new AiSummaryService(
                chatClientBuilder,
                new ClassPathResource("prompts/ai-summary-system-prompt.md"),
                new ClassPathResource("prompts/ai-summary-user-prompt.md"));
    }

    @Test
    void testGenerateSummary_Success() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("This ticket addresses a login defect.");

        String summary = aiSummaryService.generateSummary("Fix login bug", "Users cannot log in", "Confirmed by QA");

        assertEquals("This ticket addresses a login defect.", summary);
        verify(requestSpec).system(anyString());
        verify(requestSpec).user(contains("Fix login bug"));
    }

    @Test
    void testGenerateSummary_NullResponse_ReturnsFallback() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(null);

        String summary = aiSummaryService.generateSummary("Some ticket", "desc", "");

        assertEquals("AI summary unavailable.", summary);
    }

    @Test
    void testGenerateSummary_ExceptionThrown_ReturnsFallback() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("API error"));

        String summary = aiSummaryService.generateSummary("Some ticket", "desc", "");

        assertEquals("AI summary unavailable.", summary);
    }

    @Test
    void testGenerateSummary_NullInputsHandledGracefully() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Summary with null inputs");

        String summary = aiSummaryService.generateSummary(null, null, null);

        assertEquals("Summary with null inputs", summary);
    }

    @Test
    void testGenerateSummary_TrimsWhitespace() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("  Summary with spaces  \n");

        String summary = aiSummaryService.generateSummary("Ticket", "Desc", "");

        assertEquals("Summary with spaces", summary);
    }

    @Test
    void testFetchComments_ReturnsEmptyString() {
        // Placeholder implementation should return empty string
        String comments = aiSummaryService.fetchComments("12345");
        assertEquals("", comments);
    }

    @Test
    void testFetchComments_NullId() {
        String comments = aiSummaryService.fetchComments(null);
        assertEquals("", comments);
    }

    @Test
    void testAiSummaryFieldConstant() {
        assertEquals("\u2728 AI Summary", AiSummaryService.AI_SUMMARY_FIELD);
    }

    @Test
    void testConstructor_MissingResource_LoadsEmptyPrompt() {
        // When a resource doesn't exist, loadResource should handle gracefully
        Resource missingResource = new ClassPathResource("prompts/nonexistent.md");
        AiSummaryService service = new AiSummaryService(
                chatClientBuilder,
                missingResource,
                new ClassPathResource("prompts/ai-summary-user-prompt.md"));

        // Should still be able to generate (with empty system prompt)
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Works");

        String result = service.generateSummary("Test", "Desc", "");
        assertEquals("Works", result);
    }
}
