ALTER TABLE outbox_messages
    DROP CONSTRAINT chk_outbox_status,
    ADD CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'PUBLISHED')),
    ADD COLUMN claim_token UUID,
    ADD COLUMN claim_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_outbox_claim_fields
        CHECK (
            (status = 'IN_PROGRESS' AND claim_token IS NOT NULL AND claim_until IS NOT NULL)
            OR (status <> 'IN_PROGRESS' AND claim_token IS NULL AND claim_until IS NULL)
        ),
    ADD CONSTRAINT chk_outbox_attempt_count CHECK (attempt_count >= 0);

CREATE INDEX idx_outbox_pending_schedule
    ON outbox_messages(status, next_attempt_at, created_at, id);

CREATE INDEX idx_outbox_claim_expiration
    ON outbox_messages(claim_until, created_at, id)
    WHERE status = 'IN_PROGRESS';

CREATE INDEX idx_outbox_aggregate_unpublished_order
    ON outbox_messages(aggregate_id, created_at, id)
    WHERE status <> 'PUBLISHED';
