ALTER TABLE outbox_messages
    DROP CONSTRAINT chk_outbox_message_type;

ALTER TABLE outbox_messages
    ADD CONSTRAINT chk_outbox_message_type
        CHECK (message_type IN (
            'PRODUCT_CREATED_EVENT',
            'PRODUCT_UPDATED_EVENT',
            'PRODUCT_DEACTIVATED_EVENT',
            'PRODUCT_DELETED_EVENT'
        ));
