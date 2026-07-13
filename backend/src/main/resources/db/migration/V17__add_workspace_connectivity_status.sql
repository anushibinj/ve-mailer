ALTER TABLE workspaces
    ADD COLUMN connectivity_status VARCHAR(20) DEFAULT 'UNKNOWN' NOT NULL;

ALTER TABLE workspaces
    ADD COLUMN connectivity_checked_at TIMESTAMP;

ALTER TABLE workspaces
    ADD COLUMN connectivity_message VARCHAR(1000);

