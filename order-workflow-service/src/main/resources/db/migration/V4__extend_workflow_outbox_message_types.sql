ALTER TABLE outbox_messages
DROP CONSTRAINT chk_outbox_message_type;

ALTER TABLE outbox_messages ADD CONSTRAINT chk_outbox_message_type
        CHECK (message_type IN (
                                'RESERVE_STOCK_COMMAND',
                                'REJECT_ORDER_COMMAND',
                                'PROCESS_PAYMENT_COMMAND'
            ));