-- Stores a comma-separated list of admin email addresses that receive
-- system-level notifications (e.g., new user onboarding alerts).
ALTER TABLE notification_preferences
    ADD COLUMN IF NOT EXISTS admin_notification_emails TEXT;
