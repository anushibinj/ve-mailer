package com.anushibinj.veemailer.service;

import com.anushibinj.veemailer.dto.TicketCommentDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AiSummaryService {

    /** Pseudo-field name used in filter templates to enable AI summary generation. */
    public static final String AI_SUMMARY_FIELD = "\u2728 AI Summary";

    private final ChatClient chatClient;
    private final TicketCommentService ticketCommentService;
    private final String systemPromptTemplate;
    private final String userPromptTemplate;

    public AiSummaryService(
            ChatClient.Builder chatClientBuilder,
            TicketCommentService ticketCommentService,
            @Value("classpath:prompts/ai-summary-system-prompt.md") Resource systemPromptResource,
            @Value("classpath:prompts/ai-summary-user-prompt.md") Resource userPromptResource) {
        this.chatClient = chatClientBuilder.build();
        this.ticketCommentService = ticketCommentService;
        this.systemPromptTemplate = loadResource(systemPromptResource);
        this.userPromptTemplate = loadResource(userPromptResource);
    }

    /**
     * Generates an AI-powered summary for a single ticket.
     *
     * @param name        ticket title/name
     * @param description ticket description (may be null or empty)
     * @param comments    concatenated comments text (may be null or empty)
     * @return a concise summary string, or a fallback message on failure
     */
    public String generateSummary(String name, String description, String comments) {
        try {
            String userPrompt = userPromptTemplate
                    .replace("{name}", nullSafe(name))
                    .replace("{description}", nullSafe(description))
                    .replace("{comments}", nullSafe(comments));

            String systemPrompt = systemPromptTemplate
                    .replace("{todaydatetime}", nullSafe(java.time.ZonedDateTime.now().toString()));

            String result = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();

            return result != null ? result.trim() : "AI summary unavailable.";
        } catch (Exception e) {
            log.error("AI summary generation failed for ticket '{}': {}", name, e.getMessage());
            return "AI summary unavailable.";
        }
    }

    /**
     * Fetches comments for a ticket from the ticketing server and formats them
     * into a text block suitable for AI prompt input.
     *
     * @param ticketId    the numeric ID of the ticket
     * @param workspaceId the internal UUID of the workspace containing this ticket
     * @return formatted comments text, or empty string if unavailable
     */
    public String fetchComments(String ticketId, UUID workspaceId) {
        try {
            List<TicketCommentDto> comments = ticketCommentService.fetchComments(ticketId, workspaceId);
            return ticketCommentService.formatCommentsForAi(comments);
        } catch (Exception e) {
            log.error("Failed to fetch comments for ticket {}: {}", ticketId, e.getMessage());
            return "";
        }
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private String loadResource(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt resource: {}", resource.getFilename(), e);
            return "";
        }
    }
}
