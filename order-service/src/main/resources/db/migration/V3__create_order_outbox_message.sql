CREATE TABLE order_outbox_messages (
    id              UUID PRIMARY KEY,
    status          VARCHAR(20) NOT NULL,
    message_type    VARCHAR(40) NOT NULL,
    aggregate_id    UUID NOT NULL,
    payload         JSONB NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at    TIMESTAMP WITH TIME ZONE,

    CONSTRAINT chk_order_outbox_status
        CHECK (status IN ('PENDING', 'PUBLISHED')),

    CONSTRAINT chk_order_outbox_message_type
        CHECK (message_type IN ('ORDER_CHECKOUT_STARTED'))
);

CREATE INDEX idx_order_outbox_status_created_at
    ON order_outbox_messages(status, created_at);