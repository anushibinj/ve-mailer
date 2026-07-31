-- V28: Audit fields recording who created a workspace and when. Needed so the
-- "workspace pending review" email sent to Super Admins for Draft/UNKNOWN-shortcode
-- workspaces can report a Creator and Creation time. Nullable because existing rows
-- predate this tracking and have no known creator/creation time.
ALTER TABLE workspaces
    ADD COLUMN created_at TIMESTAMP,
    ADD COLUMN created_by VARCHAR(255);
