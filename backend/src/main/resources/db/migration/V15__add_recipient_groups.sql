-- =============================================================================
-- V14 : Add recipient groups (teams) for bulk subscription management
-- Admins and workspace admins can create groups of email recipients.
-- Subscribing a group to a filter creates individual EmailSubscriber rows.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- recipient_groups
-- ---------------------------------------------------------------------------
CREATE TABLE recipient_groups (
    id           UUID         NOT NULL,
    workspace_id UUID         NOT NULL,
    name         VARCHAR(255) NOT NULL,
    description  VARCHAR(500),
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by   VARCHAR(255),
    CONSTRAINT pk_recipient_groups           PRIMARY KEY (id),
    CONSTRAINT fk_recipient_groups_workspace FOREIGN KEY (workspace_id) REFERENCES workspaces (id),
    CONSTRAINT uq_recipient_groups_ws_name   UNIQUE (workspace_id, name)
);

CREATE INDEX idx_recipient_groups_workspace_id ON recipient_groups (workspace_id);

-- ---------------------------------------------------------------------------
-- recipient_group_members  (ElementCollection for RecipientGroup.memberEmails)
-- ---------------------------------------------------------------------------
CREATE TABLE recipient_group_members (
    group_id     UUID         NOT NULL,
    member_email VARCHAR(255) NOT NULL,
    CONSTRAINT pk_recipient_group_members      PRIMARY KEY (group_id, member_email),
    CONSTRAINT fk_recipient_group_members_group FOREIGN KEY (group_id) REFERENCES recipient_groups (id)
);
