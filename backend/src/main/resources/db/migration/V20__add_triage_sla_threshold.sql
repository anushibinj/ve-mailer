ALTER TABLE email_subscribers
    ADD COLUMN triage_sla_threshold VARCHAR(20);

UPDATE email_subscribers
SET triage_sla_threshold = 'GREEN'
WHERE triage_sla_threshold IS NULL;

ALTER TABLE email_subscribers
    ALTER COLUMN triage_sla_threshold SET DEFAULT 'GREEN';
