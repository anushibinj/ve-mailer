-- Add status column to workspaces table
ALTER TABLE workspaces ADD COLUMN status VARCHAR(20) DEFAULT 'ENABLED' NOT NULL;

-- Existing workspaces default to ENABLED so current users are not impacted
-- New workspaces created via application code will default to DRAFT (handled in Java entity)
