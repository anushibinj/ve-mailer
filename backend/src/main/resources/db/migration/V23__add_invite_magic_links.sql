CREATE TABLE invite_magic_links (
    id UUID NOT NULL,
    email VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    resend_count INTEGER NOT NULL DEFAULT 0,
    last_sent_at TIMESTAMP,
    used_at TIMESTAMP,
    CONSTRAINT pk_invite_magic_links PRIMARY KEY (id),
    CONSTRAINT uq_invite_magic_links_email UNIQUE (email),
    CONSTRAINT uq_invite_magic_links_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_invite_magic_links_expires_at ON invite_magic_links (expires_at);
