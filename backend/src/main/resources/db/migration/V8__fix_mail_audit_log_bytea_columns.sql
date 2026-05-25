-- V8: Fix mail_audit_log text columns that may have been created as bytea by
-- Hibernate auto-ddl on legacy deployments (before Flyway was introduced).
-- Each column is only altered if its current type is 'bytea'; fresh installs
-- where V5 ran correctly (VARCHAR) are unaffected.

DO $$
DECLARE
    col_type TEXT;
    tbl_schema TEXT;
BEGIN
    -- Resolve the schema that actually owns mail_audit_log
    -- (handles cases where Flyway uses a non-public schema)
    SELECT table_schema INTO tbl_schema
    FROM information_schema.tables
    WHERE table_name = 'mail_audit_log'
    LIMIT 1;

    IF tbl_schema IS NULL THEN
        RETURN; -- table not found; nothing to do
    END IF;

    -- filter_title (used in LOWER() LIKE search — trigger for this migration)
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_schema = tbl_schema
      AND table_name   = 'mail_audit_log'
      AND column_name  = 'filter_title';
    IF col_type = 'bytea' THEN
        EXECUTE format('ALTER TABLE %I.mail_audit_log
            ALTER COLUMN filter_title TYPE VARCHAR(255) USING convert_from(filter_title, ''UTF8'')', tbl_schema);
    END IF;

    -- workspace_title
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_schema = tbl_schema
      AND table_name   = 'mail_audit_log'
      AND column_name  = 'workspace_title';
    IF col_type = 'bytea' THEN
        EXECUTE format('ALTER TABLE %I.mail_audit_log
            ALTER COLUMN workspace_title TYPE VARCHAR(255) USING convert_from(workspace_title, ''UTF8'')', tbl_schema);
    END IF;

    -- recipient_email
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_schema = tbl_schema
      AND table_name   = 'mail_audit_log'
      AND column_name  = 'recipient_email';
    IF col_type = 'bytea' THEN
        EXECUTE format('ALTER TABLE %I.mail_audit_log
            ALTER COLUMN recipient_email TYPE VARCHAR(255) USING convert_from(recipient_email, ''UTF8'')', tbl_schema);
    END IF;

    -- mail_subject
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_schema = tbl_schema
      AND table_name   = 'mail_audit_log'
      AND column_name  = 'mail_subject';
    IF col_type = 'bytea' THEN
        EXECUTE format('ALTER TABLE %I.mail_audit_log
            ALTER COLUMN mail_subject TYPE VARCHAR(500) USING convert_from(mail_subject, ''UTF8'')', tbl_schema);
    END IF;

    -- failure_reason
    SELECT data_type INTO col_type
    FROM information_schema.columns
    WHERE table_schema = tbl_schema
      AND table_name   = 'mail_audit_log'
      AND column_name  = 'failure_reason';
    IF col_type = 'bytea' THEN
        EXECUTE format('ALTER TABLE %I.mail_audit_log
            ALTER COLUMN failure_reason TYPE VARCHAR(2000) USING convert_from(failure_reason, ''UTF8'')', tbl_schema);
    END IF;
END $$;
