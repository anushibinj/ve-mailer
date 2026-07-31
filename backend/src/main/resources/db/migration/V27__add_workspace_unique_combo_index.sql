-- V27: Enforce workspace uniqueness at the DB level using the combination of
-- (root_url, shared_space_id, workspace_id) instead of workspace_id alone.
-- A workspace is only a duplicate when ALL THREE values match an existing one;
-- sharing just one or two of the fields with another workspace remains allowed.
CREATE UNIQUE INDEX uq_workspaces_root_url_shared_space_id_workspace_id
    ON workspaces (root_url, shared_space_id, workspace_id);
