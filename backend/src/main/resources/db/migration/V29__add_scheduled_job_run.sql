-- Job identity and idempotency for scheduled digest runs. A "job instance" is one
-- (workspace_id, filter_id, slot_at) triple. job_key is UNIQUE so a duplicate tick (or a
-- second app instance) can never create a second row for the same job instance — the
-- creation gate described in the resilient-digest-jobs design doc. status/claimed_at back
-- the claim gate; dispatch_started_at backs the dispatch gate (committed before any send).
CREATE TABLE scheduled_job_run (
    id                    UUID          NOT NULL,
    job_key               VARCHAR(255)  NOT NULL,
    workspace_id          UUID,
    filter_id             UUID,
    subscription_id       UUID,
    slot_at               TIMESTAMP     NOT NULL,
    trigger_type          VARCHAR(20)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    attempt_count         INTEGER       NOT NULL DEFAULT 0,
    max_attempts          INTEGER       NOT NULL DEFAULT 1,
    claimed_at            TIMESTAMP,
    next_retry_at         TIMESTAMP,
    dispatch_started_at   TIMESTAMP,
    dispatch_completed_at TIMESTAMP,
    last_error            VARCHAR(2000),
    created_at            TIMESTAMP     NOT NULL,
    updated_at            TIMESTAMP     NOT NULL,
    CONSTRAINT pk_scheduled_job_run PRIMARY KEY (id),
    CONSTRAINT uq_scheduled_job_run_job_key UNIQUE (job_key)
);

CREATE INDEX idx_scheduled_job_run_workspace_filter ON scheduled_job_run (workspace_id, filter_id);
CREATE INDEX idx_scheduled_job_run_status_next_retry ON scheduled_job_run (status, next_retry_at);
CREATE INDEX idx_scheduled_job_run_status_dispatch_started ON scheduled_job_run (status, dispatch_started_at);

-- Persists the subscriber ids a run must notify, so a retry reloads fresh EmailSubscriber
-- rows from the DB rather than trusting a stale in-memory list from the failed attempt.
CREATE TABLE scheduled_job_run_subscriber_ids (
    job_run_id     UUID NOT NULL,
    subscriber_id  UUID NOT NULL,
    CONSTRAINT fk_job_run_subscriber_ids_job_run_id FOREIGN KEY (job_run_id)
        REFERENCES scheduled_job_run (id) ON DELETE CASCADE
);

CREATE INDEX idx_job_run_subscriber_ids_job_run_id ON scheduled_job_run_subscriber_ids (job_run_id);
