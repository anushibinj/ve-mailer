package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.TicketCommentDto;
import com.anushibinj.veemailer.model.Workspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.core.io.ClassPathResource;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiSummaryServiceTest {

    @Mock
    private DynamicAiClientService dynamicAiClientService;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    @Mock
    private TicketCommentService ticketCommentService;

    private AiSummaryService aiSummaryService;

    @BeforeEach
    void setUp() {
        // lenient: not all tests exercise the AI path (e.g. fetchComments tests)
        lenient().when(dynamicAiClientService.getChatClient()).thenReturn(chatClient);
        aiSummaryService = new AiSummaryService(
                dynamicAiClientService,
                ticketCommentService,
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

        String summary = aiSummaryService.generateSummary("1001", "Fix login bug", "Users cannot log in", "Confirmed by QA", null);

        assertEquals("This ticket addresses a login defect.", summary);
        verify(requestSpec).system(anyString());
        // User prompt must include ticket id, name, description and comments so the
        // AI model has full context about what the ticket is asking for.
        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userPromptCaptor.capture());
        String capturedUserPrompt = userPromptCaptor.getValue();
        assertTrue(capturedUserPrompt.contains("1001"));
        assertTrue(capturedUserPrompt.contains("Fix login bug"));
        assertTrue(capturedUserPrompt.contains("Users cannot log in"));
        assertTrue(capturedUserPrompt.contains("Confirmed by QA"));
    }

    @Test
    void testGenerateSummary_NullResponse_ReturnsFallback() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(null);

        String summary = aiSummaryService.generateSummary("1002", "Some ticket", "desc", "User comment here", null);

        assertEquals("Unable to reach AI now", summary);
    }

    @Test
    void testGenerateSummary_BlankComments_ReturnsEarlyFallback() {
        String summary = aiSummaryService.generateSummary("1003", "Ticket", "Desc", "", null);
        assertEquals("There is not enough comment data to understand the context of the ticket.", summary);

        String summaryNull = aiSummaryService.generateSummary("1003", "Ticket", "Desc", null, null);
        assertEquals("There is not enough comment data to understand the context of the ticket.", summaryNull);

        verifyNoInteractions(chatClient);
    }

    @Test
    void testGenerateSummary_ExceptionThrown_ReturnsFallback() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("API error"));

        String summary = aiSummaryService.generateSummary("1002", "Some ticket", "desc", "User comment here", null);

        assertEquals("Unable to reach AI now", summary);
    }

    @Test
    void testGenerateSummary_NullInputsHandledGracefully() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Summary with null inputs");

        String summary = aiSummaryService.generateSummary(null, null, null, "Some comment", null);

        assertEquals("Summary with null inputs", summary);
    }

    @Test
    void testGenerateSummary_TrimsWhitespace() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("  Summary with spaces  \n");

        String summary = aiSummaryService.generateSummary("1004", "Ticket", "Desc", "User comment", null);

        assertEquals("Summary with spaces", summary);
    }

    @Test
    void testGenerateSummary_PhaseAgeSubstitutedIntoSystemPrompt() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Summary");

        aiSummaryService.generateSummary("1004", "Ticket", "Desc", "User comment", 7);

        verify(requestSpec).system(contains("7 days"));
    }

    @Test
    void testGenerateSummary_NullPhaseAgeRendersAsUnknown() {
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Summary");

        aiSummaryService.generateSummary("1004", "Ticket", "Desc", "User comment", null);

        verify(requestSpec).system(contains("unknown days"));
    }

    @Test
    void testFetchComments_DelegatesToTicketCommentService() {
        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        List<TicketCommentDto> comments = List.of(
                TicketCommentDto.builder().authorName("John").text("Looks good").build(),
                TicketCommentDto.builder().authorName("Jane").text("Needs fix").build()
        );
        when(ticketCommentService.fetchComments("12345", workspace)).thenReturn(comments);
        when(ticketCommentService.formatCommentsForAi(comments)).thenReturn("Comment by John:\nLooks good\n\nComment by Jane:\nNeeds fix");

        String result = aiSummaryService.fetchComments("12345", workspace);

        assertEquals("Comment by John:\nLooks good\n\nComment by Jane:\nNeeds fix", result);
        verify(ticketCommentService).fetchComments("12345", workspace);
        verify(ticketCommentService).formatCommentsForAi(comments);
    }

    @Test
    void testFetchComments_ReturnsEmptyOnException() {
        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        when(ticketCommentService.fetchComments(any(), any(Workspace.class))).thenThrow(new RuntimeException("Network error"));

        String result = aiSummaryService.fetchComments("12345", workspace);

        assertEquals("", result);
    }

    @Test
    void testFetchComments_EmptyCommentsList() {
        Workspace workspace = new Workspace();
        workspace.setId(UUID.randomUUID());
        when(ticketCommentService.fetchComments("999", workspace)).thenReturn(Collections.emptyList());
        when(ticketCommentService.formatCommentsForAi(Collections.emptyList())).thenReturn("");

        String result = aiSummaryService.fetchComments("999", workspace);

        assertEquals("", result);
    }

    @Test
    void testAiSummaryFieldConstant() {
        assertEquals("\u2728 AI Summary", AiSummaryService.AI_SUMMARY_FIELD);
    }

    @Test
    void testConstructor_MissingResource_LoadsEmptyPrompt() {
        // When a resource doesn't exist, loadResource should handle gracefully
        AiSummaryService service = new AiSummaryService(
                dynamicAiClientService,
                ticketCommentService,
                new ClassPathResource("prompts/nonexistent.md"),
                new ClassPathResource("prompts/ai-summary-user-prompt.md"));

        // Should still be able to generate (with empty system prompt)
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("Works");

        String result = service.generateSummary("1005", "Test", "Desc", "Some comment", null);
        assertEquals("Works", result);
    }
}
