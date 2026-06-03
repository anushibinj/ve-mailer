-- =============================================================================
-- V12 : Add WORKSPACE_ADMIN role and workspace_admins mapping table
-- =============================================================================

-- Insert the new WORKSPACE_ADMIN role
INSERT INTO roles (id, role_name) VALUES (RANDOM_UUID(), 'WORKSPACE_ADMIN');

-- ---------------------------------------------------------------------------
-- workspace_admins (maps users to workspaces they can administer)
-- ---------------------------------------------------------------------------
CREATE TABLE workspace_admins (
    id           UUID      NOT NULL,
    workspace_id UUID      NOT NULL,
    user_id      UUID      NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(255),
    CONSTRAINT pk_workspace_admins             PRIMARY KEY (id),
    CONSTRAINT fk_workspace_admins_workspace   FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT fk_workspace_admins_user        FOREIGN KEY (user_id)      REFERENCES app_users (id),
    CONSTRAINT uq_workspace_admins_ws_user     UNIQUE (workspace_id, user_id)
);

CREATE INDEX idx_workspace_admins_workspace_id ON workspace_admins (workspace_id);
CREATE INDEX idx_workspace_admins_user_id      ON workspace_admins (user_id);
