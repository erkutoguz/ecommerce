ALTER TABLE order_sagas
DROP CONSTRAINT chk_saga_state;

ALTER TABLE order_sagas ADD CONSTRAINT chk_saga_state
        CHECK (state IN (
                         'STOCK_RESERVATION_PENDING',
                         'PAYMENT_PENDING',
                         'PAYMENT_UNKNOWN',
                         'STOCK_CONFIRMATION_PENDING',
                         'ORDER_CONFIRMATION_PENDING',
                         'STOCK_RELEASE_PENDING',
                         'COMPLETED',
                         'FAILED',
                         'ORDER_REJECTION_PENDING'
            ));
