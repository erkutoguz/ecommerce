ALTER TABLE outbox_messages
DROP CONSTRAINT chk_outbox_message_type;

ALTER TABLE outbox_messages ADD CONSTRAINT chk_outbox_message_type
    CHECK (message_type IN (
                            'ORDER_CHECKOUT_STARTED',
                            'ORDER_REJECTED_EVENT',
                            'ORDER_CONFIRMED_EVENT'
        ));