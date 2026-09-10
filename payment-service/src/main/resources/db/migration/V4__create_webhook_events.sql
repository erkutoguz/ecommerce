CREATE TABLE webhook_events
(
    event_id           VARCHAR(255) PRIMARY KEY,
    event_type         VARCHAR(255)             NOT NULL,
    provider_object_id VARCHAR(255),
    received_at        TIMESTAMP WITH TIME ZONE NOT NULL
);