-- V9: Fix mail_audit_log text columns using pg_catalog.pg_attribute for type
-- detection (V8 used information_schema which silently returned NULL, causing
-- all IF checks to be skipped without error).
DO $$
DECLARE
    v_typname TEXT;
BEGIN

    -- filter_title
    SELECT t.typname INTO v_typname
    FROM   pg_catalog.pg_attribute  a
    JOIN   pg_catalog.pg_class      c ON c.oid = a.attrelid
    JOIN   pg_catalog.pg_type       t ON t.oid = a.atttypid
    JOIN   pg_catalog.pg_namespace  n ON n.oid = c.relnamespace
    WHERE  c.relname  = 'mail_audit_log'
      AND  c.relkind  = 'r'
      AND  a.attname  = 'filter_title'
      AND  a.attnum   > 0
      AND  NOT a.attisdropped
      AND  n.nspname NOT IN ('pg_catalog', 'information_schema');

    IF v_typname = 'bytea' THEN
        ALTER TABLE mail_audit_log
            ALTER COLUMN filter_title TYPE VARCHAR(255)
            USING convert_from(filter_title, 'UTF8');
    END IF;

    -- workspace_title
    SELECT t.typname INTO v_typname
    FROM   pg_catalog.pg_attribute  a
    JOIN   pg_catalog.pg_class      c ON c.oid = a.attrelid
    JOIN   pg_catalog.pg_type       t ON t.oid = a.atttypid
    JOIN   pg_catalog.pg_namespace  n ON n.oid = c.relnamespace
    WHERE  c.relname  = 'mail_audit_log'
      AND  c.relkind  = 'r'
      AND  a.attname  = 'workspace_title'
      AND  a.attnum   > 0
      AND  NOT a.attisdropped
      AND  n.nspname NOT IN ('pg_catalog', 'information_schema');

    IF v_typname = 'bytea' THEN
        ALTER TABLE mail_audit_log
            ALTER COLUMN workspace_title TYPE VARCHAR(255)
            USING convert_from(workspace_title, 'UTF8');
    END IF;

    -- recipient_email
    SELECT t.typname INTO v_typname
    FROM   pg_catalog.pg_attribute  a
    JOIN   pg_catalog.pg_class      c ON c.oid = a.attrelid
    JOIN   pg_catalog.pg_type       t ON t.oid = a.atttypid
    JOIN   pg_catalog.pg_namespace  n ON n.oid = c.relnamespace
    WHERE  c.relname  = 'mail_audit_log'
      AND  c.relkind  = 'r'
      AND  a.attname  = 'recipient_email'
      AND  a.attnum   > 0
      AND  NOT a.attisdropped
      AND  n.nspname NOT IN ('pg_catalog', 'information_schema');

    IF v_typname = 'bytea' THEN
        ALTER TABLE mail_audit_log
            ALTER COLUMN recipient_email TYPE VARCHAR(255)
            USING convert_from(recipient_email, 'UTF8');
    END IF;

    -- mail_subject
    SELECT t.typname INTO v_typname
    FROM   pg_catalog.pg_attribute  a
    JOIN   pg_catalog.pg_class      c ON c.oid = a.attrelid
    JOIN   pg_catalog.pg_type       t ON t.oid = a.atttypid
    JOIN   pg_catalog.pg_namespace  n ON n.oid = c.relnamespace
    WHERE  c.relname  = 'mail_audit_log'
      AND  c.relkind  = 'r'
      AND  a.attname  = 'mail_subject'
      AND  a.attnum   > 0
      AND  NOT a.attisdropped
      AND  n.nspname NOT IN ('pg_catalog', 'information_schema');

    IF v_typname = 'bytea' THEN
        ALTER TABLE mail_audit_log
            ALTER COLUMN mail_subject TYPE VARCHAR(500)
            USING convert_from(mail_subject, 'UTF8');
    END IF;

    -- failure_reason
    SELECT t.typname INTO v_typname
    FROM   pg_catalog.pg_attribute  a
    JOIN   pg_catalog.pg_class      c ON c.oid = a.attrelid
    JOIN   pg_catalog.pg_type       t ON t.oid = a.atttypid
    JOIN   pg_catalog.pg_namespace  n ON n.oid = c.relnamespace
    WHERE  c.relname  = 'mail_audit_log'
      AND  c.relkind  = 'r'
      AND  a.attname  = 'failure_reason'
      AND  a.attnum   > 0
      AND  NOT a.attisdropped
      AND  n.nspname NOT IN ('pg_catalog', 'information_schema');

    IF v_typname = 'bytea' THEN
        ALTER TABLE mail_audit_log
            ALTER COLUMN failure_reason TYPE VARCHAR(2000)
            USING convert_from(failure_reason, 'UTF8');
    END IF;

END $$;
