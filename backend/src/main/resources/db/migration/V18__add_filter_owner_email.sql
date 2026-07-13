ALTER TABLE filters
    ADD COLUMN owner_email VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_filters_workspace_owner_email
    ON filters (workspace_id, owner_email);
