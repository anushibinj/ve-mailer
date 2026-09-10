-- Correlates mail_audit_log rows to the scheduled_job_run that produced them, so a job's
-- retry attempts update the same recipient row in place instead of inserting a new one each
-- time (row counts and existing analytics aggregates stay unchanged). subscription_id is part
-- of the uniqueness key because one address can be both an individual subscriber and a group
-- member for the same filter, in which case it legitimately gets two rows per job run.
ALTER TABLE mail_audit_log ADD COLUMN job_run_id UUID;

ALTER TABLE mail_audit_log
    ADD CONSTRAINT fk_mail_audit_log_job_run_id FOREIGN KEY (job_run_id)
    REFERENCES scheduled_job_run (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX uq_mail_audit_log_job_run_subscription_recipient
    ON mail_audit_log (job_run_id, subscription_id, recipient_email)
    WHERE job_run_id IS NOT NULL;

-- Backs the minimum-dispatch-gap recency check, which queries "does this subscription already
-- have a SUCCESS row newer than now - minDispatchGap" on every dispatch. V5 indexed sent_at but
-- never subscription_id, so that query would otherwise scan the whole table.
CREATE INDEX idx_mail_audit_log_subscription_sent_at ON mail_audit_log (subscription_id, sent_at);
