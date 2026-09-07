CREATE TABLE order_sagas
(
    order_id    UUID PRIMARY KEY,
    customer_id UUID                     NOT NULL,
    state       VARCHAR(50)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_saga_state
        CHECK (state IN (
                         'STOCK_RESERVATION_PENDING',
                         'PAYMENT_PENDING',
                         'PAYMENT_UNKNOWN',
                         'STOCK_CONFIRMATION_PENDING',
                         'STOCK_RELEASE_PENDING',
                         'COMPLETED',
                         'FAILED'
            ))
);
