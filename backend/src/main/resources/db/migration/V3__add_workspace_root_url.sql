-- =============================================================================
-- V3 : Add root_url column to workspaces table
-- =============================================================================

ALTER TABLE workspaces ADD COLUMN root_url VARCHAR(255);
