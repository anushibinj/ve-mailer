-- =============================================================================
-- V2 : Add notification_preferences table
-- Stores SMTP configuration for outbound email (previously in application.properties).
-- =============================================================================

CREATE TABLE notification_preferences (
    id                 UUID         NOT NULL,
    host               VARCHAR(255) NOT NULL,
    port               INT          NOT NULL,
    username           VARCHAR(255) NOT NULL,
    password           VARCHAR(255) NOT NULL,
    start_tls_enabled  BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_notification_preferences PRIMARY KEY (id)
);
