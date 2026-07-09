-- Make group subscriptions a first-class entity:
-- A single EmailSubscriber row with group_id set represents a group subscription.
-- At send time, the group is expanded to its current member emails dynamically.

ALTER TABLE email_subscribers
    ADD COLUMN group_id UUID,
    ADD CONSTRAINT fk_email_subscribers_group_id
        FOREIGN KEY (group_id) REFERENCES recipient_groups(id) ON DELETE CASCADE;

-- Prevent the same group from being subscribed twice to the same filter in the same workspace.
CREATE UNIQUE INDEX uq_email_subscribers_group_filter
    ON email_subscribers (group_id, workspace_id, filter_id)
    WHERE group_id IS NOT NULL;
