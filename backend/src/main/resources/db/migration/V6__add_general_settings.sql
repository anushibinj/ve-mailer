-- =============================================================================
-- V6 : Add general_settings table
-- Stores application-wide settings that can be managed at runtime through
-- the Admin Control Panel. Falls back to application.properties defaults
-- when no row exists.
-- =============================================================================

CREATE TABLE general_settings (
    id          UUID NOT NULL,
    query_limit INT  NOT NULL DEFAULT 25,
    CONSTRAINT pk_general_settings PRIMARY KEY (id)
);
