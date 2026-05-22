CREATE TABLE mail_audit_log (
    id                 UUID         NOT NULL,
    workspace_id       UUID,
    workspace_title    VARCHAR(255),
    recipient_email    VARCHAR(255) NOT NULL,
    filter_template_id UUID,
    filter_title       VARCHAR(255),
    subscription_id    UUID,
    user_id            UUID,
    mail_subject       VARCHAR(500),
    ticket_count       INTEGER      NOT NULL DEFAULT 0,
    delivery_status    VARCHAR(20)  NOT NULL,
    failure_reason     VARCHAR(2000),
    sent_at            TIMESTAMP    NOT NULL,
    duration_ms        BIGINT,
    CONSTRAINT pk_mail_audit_log PRIMARY KEY (id)
);

CREATE INDEX idx_mail_audit_log_sent_at ON mail_audit_log (sent_at);
CREATE INDEX idx_mail_audit_log_workspace_id ON mail_audit_log (workspace_id);
CREATE INDEX idx_mail_audit_log_recipient_email ON mail_audit_log (recipient_email);
CREATE INDEX idx_mail_audit_log_filter_template_id ON mail_audit_log (filter_template_id);
CREATE INDEX idx_mail_audit_log_delivery_status ON mail_audit_log (delivery_status);
