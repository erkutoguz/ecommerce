CREATE TABLE order_sagas
(
    order_id     UUID PRIMARY KEY,
    customer_id  UUID                     NOT NULL,
    version      BIGINT                   NOT NULL DEFAULT 0,
    currency     VARCHAR(3)               NOT NULL,
    total_amount DECIMAL(19, 2)           NOT NULL,
    state        VARCHAR(50)              NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_order_saga_currency
        CHECK (currency IN ('TRY', 'USD', 'EUR')),

    CONSTRAINT chk_saga_state
        CHECK (state IN (
                         'STOCK_RESERVATION_PENDING',
                         'PAYMENT_PENDING',
                         'PAYMENT_UNKNOWN',
                         'STOCK_CONFIRMATION_PENDING',
                         'STOCK_RELEASE_PENDING',
                         'COMPLETED',
                         'FAILED',
                         'ORDER_REJECTION_PENDING'
            ))
);
