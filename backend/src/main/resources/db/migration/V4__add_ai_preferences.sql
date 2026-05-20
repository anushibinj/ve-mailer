-- =============================================================================
-- V4 : Add ai_preferences table
-- Stores AI provider configuration (previously in application-dev.properties).
-- API key is stored encrypted at-rest by the application layer.
-- =============================================================================

CREATE TABLE ai_preferences (
    id                    UUID         NOT NULL,
    api_key               VARCHAR(500) NOT NULL,
    base_url              VARCHAR(255) NOT NULL,
    chat_completions_path VARCHAR(255) NOT NULL,
    model                 VARCHAR(255) NOT NULL,
    CONSTRAINT pk_ai_preferences PRIMARY KEY (id)
);
