ALTER TABLE filters
    ADD COLUMN is_public BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE filters
SET is_public = TRUE
WHERE owner_email IS NULL;

CREATE INDEX IF NOT EXISTS idx_filters_workspace_visibility
    ON filters (workspace_id, is_public, owner_email);
