package com.anushibinj.veemailer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating/updating AI preferences.
 * The API key can be null/blank/"(unchanged)" to preserve the existing value on update.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPreferencesUpdateDto {

    // Optional on update — if null/blank/"(unchanged)", the stored API key is preserved
    private String apiKey;

    @NotBlank(message = "Base URL is required")
    @Pattern(regexp = "^https?://.*", message = "Base URL must start with http:// or https://")
    private String baseUrl;

    @NotBlank(message = "Chat completions path is required")
    @Pattern(regexp = "^/.*", message = "Chat completions path must start with /")
    private String chatCompletionsPath;

    @NotBlank(message = "Model is required")
    private String model;
}
