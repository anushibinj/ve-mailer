-- V11: Robustly fix mail_audit_log text columns that are still typed as bytea
-- on legacy deployments where Hibernate auto-DDL ran before Flyway was introduced.
--
-- V8 used information_schema.columns to detect the schema; this silently returns
-- NULL in certain Flyway search_path configurations, causing the IF checks to be
-- skipped without error.
--
-- V9 used pg_catalog.pg_attribute but joined pg_namespace with a NOT IN filter
-- ('pg_catalog', 'information_schema') which could silently skip the lookup when
-- the resolved namespace OID didn't survive the join under certain schema setups.
--
-- This migration iterates over all affected columns using pg_catalog WITHOUT the
-- pg_namespace join, which reliably finds the table regardless of search_path or
-- schema configuration. If no columns are bytea (e.g., V5 ran correctly or V8/V9
-- succeeded), the loop body never executes — this migration is fully idempotent.

DO $$
DECLARE
    v_col  RECORD;
    v_sql  TEXT;
BEGIN
    FOR v_col IN
        SELECT a.attname
        FROM   pg_catalog.pg_attribute  a
        JOIN   pg_catalog.pg_class      c ON c.oid = a.attrelid
        JOIN   pg_catalog.pg_type       t ON t.oid = a.atttypid
        WHERE  c.relname  = 'mail_audit_log'
          AND  c.relkind  = 'r'
          AND  a.attname  IN ('filter_title', 'workspace_title', 'recipient_email',
                              'mail_subject', 'failure_reason')
          AND  a.attnum   > 0
          AND  NOT a.attisdropped
          AND  t.typname  = 'bytea'
        ORDER BY a.attnum
    LOOP
        v_sql := format(
            'ALTER TABLE mail_audit_log ALTER COLUMN %I TYPE TEXT USING convert_from(%I, ''UTF8'')',
            v_col.attname, v_col.attname
        );
        EXECUTE v_sql;
        RAISE NOTICE 'V11: Converted mail_audit_log.% from bytea to text', v_col.attname;
    END LOOP;
END $$;
