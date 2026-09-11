ALTER TABLE outbox_messages
DROP CONSTRAINT chk_outbox_message_type;

ALTER TABLE outbox_messages ADD CONSTRAINT chk_outbox_message_type
    CHECK (message_type IN (
                            'STOCK_RESERVED_EVENT',
                            'STOCK_RESERVATION_FAILED_EVENT',
                            'STOCK_RESERVATION_CONFIRMED_EVENT',
                            'STOCK_RESERVATION_RELEASED_EVENT'
        ));
