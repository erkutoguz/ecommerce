CREATE INDEX idx_order_sagas_active_created_at
    ON order_sagas (created_at)
    WHERE state NOT IN ('COMPLETED', 'FAILED');
