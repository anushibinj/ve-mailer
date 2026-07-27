CREATE TABLE issue_reports (
    id UUID NOT NULL,
    reporter_email VARCHAR(255),
    message TEXT,
    screenshot_data BYTEA,
    screenshot_content_type VARCHAR(255),
    screenshot_file_name VARCHAR(255),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_issue_reports PRIMARY KEY (id)
);

CREATE INDEX idx_issue_reports_status ON issue_reports(status);
CREATE INDEX idx_issue_reports_created_at ON issue_reports(created_at);
