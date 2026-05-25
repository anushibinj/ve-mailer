-- =============================================================================
-- V7 : Add from_address and requires_auth to notification_preferences
--
-- from_address : the email address placed in the From: header.
--                Decoupled from the SMTP auth username so admins can send
--                as a shared mailbox while authenticating as themselves.
-- requires_auth: when FALSE the SMTP session is sent unauthenticated
--                (port-25 relay style), so username/password become optional.
-- =============================================================================

ALTER TABLE notification_preferences
    ADD COLUMN from_address  VARCHAR(255),
    ADD COLUMN requires_auth BOOLEAN NOT NULL DEFAULT TRUE;

-- username and password are only mandatory when requires_auth = TRUE,
-- so relax the NOT NULL constraints and let the application layer validate.
ALTER TABLE notification_preferences
    ALTER COLUMN username DROP NOT NULL,
    ALTER COLUMN password DROP NOT NULL;
