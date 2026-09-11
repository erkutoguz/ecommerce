ALTER TABLE order_sagas
    ADD COLUMN failure_reason VARCHAR(40);

ALTER TABLE order_sagas
    ADD CONSTRAINT chk_order_saga_failure_reason
        CHECK (failure_reason IS NULL OR failure_reason IN ('PAYMENT_EXPIRED', 'RESERVATION_EXPIRED'));
