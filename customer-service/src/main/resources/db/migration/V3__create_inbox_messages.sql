CREATE TABLE inbox_messages
(
    message_id   UUID PRIMARY KEY,
    message_type VARCHAR(50)              NOT NULL,
    aggregate_id UUID                     NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_inbox_message_type_not_blank
        CHECK (btrim(message_type) <> '')
);

CREATE INDEX idx_inbox_messages_aggregate_id
    ON inbox_messages (aggregate_id);