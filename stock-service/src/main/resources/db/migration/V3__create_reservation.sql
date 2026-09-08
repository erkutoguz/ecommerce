CREATE TABLE reservations
(
    order_id   UUID PRIMARY KEY,
    status     VARCHAR(40)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_reservation_status
        CHECK (status IN ('RESERVED',
                          'CONFIRMED',
                          'RELEASED',
                          'EXPIRED'))
);

CREATE INDEX idx_reservations_status
    ON reservations (status);

CREATE TABLE reservation_items
(
    order_id   UUID    NOT NULL,
    product_id UUID    NOT NULL,
    quantity   INTEGER NOT NULL,

    CONSTRAINT pk_reservation_items
        PRIMARY KEY (order_id, product_id),

    CONSTRAINT fk_reservation_items_reservation
        FOREIGN KEY (order_id)
            REFERENCES reservations (order_id)
            ON DELETE CASCADE,

    CONSTRAINT chk_reservation_items_quantity
        CHECK (quantity > 0)
);