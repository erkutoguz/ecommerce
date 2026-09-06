CREATE TABLE outbox_messages (
    id              UUID PRIMARY KEY,
    status          VARCHAR(20) NOT NULL,
    message_type    VARCHAR(40) NOT NULL,
    aggregate_id    UUID NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED')),

    CONSTRAINT chk_outbox_message_type
        CHECK (message_type IN ('RESERVE_STOCK_COMMAND'))
);

CREATE INDEX idx_outbox_status_created_at
    ON outbox_messages(status, created_at);
