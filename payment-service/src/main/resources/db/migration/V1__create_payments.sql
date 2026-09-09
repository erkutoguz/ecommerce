CREATE TABLE payments
(
    order_id            UUID PRIMARY KEY,
    version             BIGINT                   NOT NULL DEFAULT 0,
    total_amount        DECIMAL(19, 2)           NOT NULL,
    currency            VARCHAR(3)               NOT NULL,
    status              VARCHAR(40)              NOT NULL,
    provider_payment_id VARCHAR(255),
    checkout_url        TEXT,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    processed_at        TIMESTAMP WITH TIME ZONE,

    CONSTRAINT uk_payments_provider_payment_id
        UNIQUE (provider_payment_id),

    CONSTRAINT chk_payments_currency
        CHECK (currency IN (
                            'TRY',
                            'USD',
                            'EUR'
            )),

    CONSTRAINT chk_payments_status
        CHECK (status IN (
                          'PROCESSING',
                          'AWAITING_CUSTOMER_ACTION',
                          'COMPLETED',
                          'FAILED',
                          'UNKNOWN'
            )),

    CONSTRAINT chk_payments_total_amount
        CHECK (total_amount > 0)
);