-- =============================================================================
-- V13 : Add must_set_password flag to app_users
-- =============================================================================
-- Allows admins to onboard users whose accounts are created without a known
-- password. The flag is cleared when the user accepts their invite and sets
-- their own password.

ALTER TABLE app_users ADD COLUMN must_set_password BOOLEAN NOT NULL DEFAULT FALSE;
