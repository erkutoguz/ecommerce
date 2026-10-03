ALTER TABLE outbox_messages
    ADD COLUMN traceparent VARCHAR(55),
    ADD COLUMN tracestate VARCHAR(512);
