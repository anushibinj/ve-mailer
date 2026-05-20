package com.anushibinj.veemailer.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Stores AI provider configuration in the database.
 * Replaces static {@code spring.ai.openai.*} application properties so that
 * AI settings can be managed at runtime through the Admin Control Panel.
 */
@Entity
@Table(name = "ai_preferences")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPreferences {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** API key for the AI provider (stored as-is; never returned to the frontend). */
    @Column(nullable = false)
    private String apiKey;

    /** Base URL of the AI provider endpoint, e.g. {@code https://api.openai.com}. */
    @Column(nullable = false)
    private String baseUrl;

    /** Path used for chat completion requests, e.g. {@code /chat/completions}. */
    @Column(nullable = false)
    private String chatCompletionsPath;

    /** Model identifier, e.g. {@code gpt-4.1-mini}. */
    @Column(nullable = false)
    private String model;
}
