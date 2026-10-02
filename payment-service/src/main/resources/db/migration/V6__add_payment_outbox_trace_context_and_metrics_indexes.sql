ALTER TABLE outbox_messages
    ADD COLUMN traceparent VARCHAR(55),
    ADD COLUMN tracestate VARCHAR(512);

CREATE INDEX idx_payments_status_created_at
    ON payments(status, created_at);
