package com.anushibinj.veemailer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for AI preferences. The API key is always masked — never the real value.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiPreferencesResponseDto {

    // Always "(unchanged)" — the real API key is never returned
    private String apiKey;
    private String baseUrl;
    private String chatCompletionsPath;
    private String model;
    private boolean configured;
}
