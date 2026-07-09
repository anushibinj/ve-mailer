-- Track how many times an OTP has been resent so we can enforce an
-- incrementing cooldown: wait = (resend_count + 1) * 30 seconds.
ALTER TABLE otp_requests ADD COLUMN resend_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE otp_requests ADD COLUMN last_sent_at TIMESTAMP;

-- Seed existing rows so their next resend starts the 30 s cooldown.
UPDATE otp_requests SET last_sent_at = NOW() WHERE last_sent_at IS NULL;
