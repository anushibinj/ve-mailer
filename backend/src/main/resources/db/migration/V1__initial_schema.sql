-- =============================================================================
-- V1 : Initial schema
-- All constraint names are explicit so renames / drops can target them by name.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- roles
-- ---------------------------------------------------------------------------
CREATE TABLE roles (
    id        UUID         NOT NULL,
    role_name VARCHAR(255) NOT NULL,
    CONSTRAINT pk_roles             PRIMARY KEY (id),
    CONSTRAINT uq_roles_role_name   UNIQUE      (role_name)
);

-- ---------------------------------------------------------------------------
-- app_users
-- ---------------------------------------------------------------------------
CREATE TABLE app_users (
    id            UUID         NOT NULL,
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT pk_app_users         PRIMARY KEY (id),
    CONSTRAINT uq_app_users_email   UNIQUE      (email)
);

-- ---------------------------------------------------------------------------
-- user_roles  (ManyToMany join table)
-- ---------------------------------------------------------------------------
CREATE TABLE user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles            PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user_id    FOREIGN KEY (user_id) REFERENCES app_users (id),
    CONSTRAINT fk_user_roles_role_id    FOREIGN KEY (role_id) REFERENCES roles     (id)
);

-- ---------------------------------------------------------------------------
-- workspaces
-- ---------------------------------------------------------------------------
CREATE TABLE workspaces (
    id               UUID         NOT NULL,
    title            VARCHAR(255),
    shared_space_id  VARCHAR(255),
    workspace_id     VARCHAR(255),
    client_id        VARCHAR(255),
    client_key       VARCHAR(255),
    CONSTRAINT pk_workspaces PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- filters
-- ---------------------------------------------------------------------------
CREATE TABLE filters (
    id           UUID         NOT NULL,
    title        VARCHAR(255),
    description  VARCHAR(255),
    workspace_id UUID         NOT NULL,
    entity_type  VARCHAR(255),
    fields       TEXT,
    criteria     TEXT,
    CONSTRAINT pk_filters               PRIMARY KEY (id),
    CONSTRAINT fk_filters_workspace_id  FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
);

-- ---------------------------------------------------------------------------
-- email_subscribers
-- ---------------------------------------------------------------------------
CREATE TABLE email_subscribers (
    id              UUID        NOT NULL,
    filter_id       UUID        NOT NULL,
    workspace_id    UUID        NOT NULL,
    recipient_email VARCHAR(255),
    frequency       VARCHAR(50),
    schedule_type   VARCHAR(50),
    status          VARCHAR(50),
    CONSTRAINT pk_email_subscribers                 PRIMARY KEY (id),
    CONSTRAINT fk_email_subscribers_filter_id       FOREIGN KEY (filter_id)    REFERENCES filters    (id),
    CONSTRAINT fk_email_subscribers_workspace_id    FOREIGN KEY (workspace_id) REFERENCES workspaces (id)
);

-- ---------------------------------------------------------------------------
-- subscriber_scheduled_hours  (ElementCollection for EmailSubscriber)
-- ---------------------------------------------------------------------------
CREATE TABLE subscriber_scheduled_hours (
    subscriber_id  UUID    NOT NULL,
    scheduled_hour INTEGER,
    CONSTRAINT fk_subscriber_hours_subscriber_id FOREIGN KEY (subscriber_id) REFERENCES email_subscribers (id)
);

-- ---------------------------------------------------------------------------
-- otp_requests
-- ---------------------------------------------------------------------------
CREATE TABLE otp_requests (
    id          UUID         NOT NULL,
    email       VARCHAR(255),
    otp_hash    VARCHAR(255),
    action_type VARCHAR(50),
    payload     TEXT,
    expires_at  TIMESTAMP,
    CONSTRAINT pk_otp_requests PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------------
-- refresh_tokens
-- ---------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id         UUID         NOT NULL,
    user_id    UUID         NOT NULL,
    token      VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT pk_refresh_tokens            PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_token      UNIQUE      (token),
    CONSTRAINT fk_refresh_tokens_user_id    FOREIGN KEY (user_id) REFERENCES app_users (id)
);
