ALTER TABLE outbox_messages
DROP CONSTRAINT chk_outbox_message_type;

ALTER TABLE outbox_messages ADD CONSTRAINT chk_outbox_message_type
    CHECK (message_type IN (
                            'RESERVE_STOCK_COMMAND',
                            'RELEASE_STOCK_RESERVATION_COMMAND',
                            'REJECT_ORDER_COMMAND',
                            'INITIATE_PAYMENT_COMMAND',
                            'CONFIRM_STOCK_RESERVATION_COMMAND',
                            'MARK_ORDER_STOCK_RESERVED_COMMAND',
                            'MARK_ORDER_PAYMENT_COMPLETED_COMMAND',
                            'CONFIRM_ORDER_COMMAND'
        ));
