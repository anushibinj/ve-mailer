-- =============================================================================
-- V12 : Add workspace_admins mapping table
-- =============================================================================
-- Note: The WORKSPACE_ADMIN role row is seeded by AdminBootstrapService at
-- startup (consistent with how ADMIN and MEMBER roles are created), so it is
-- not inserted here. This keeps the migration database-agnostic (H2/PostgreSQL).

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
