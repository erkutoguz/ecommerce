CREATE INDEX idx_reservations_active_created_at
    ON reservations (created_at)
    WHERE status = 'RESERVED';
