CREATE TABLE orders (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL,
    status          VARCHAR(30) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    total_amount    DECIMAL(19,2) NOT NULL,
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL,

    CONSTRAINT chk_order_status
                    CHECK(status IN ('PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED')),

    CONSTRAINT chk_order_currency
                    CHECK(currency IN ('TRY', 'EUR', 'USD')),

    CONSTRAINT chk_order_total_amount
                    CHECK(total_amount > 0)
);

CREATE INDEX idx_orders_customer_id
    ON orders(customer_id);

CREATE INDEX idx_orders_created_at
    ON orders(created_at);

CREATE TABLE order_items(
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    item_id UUID NOT NULL,
    item_name_snapshot VARCHAR(255) NOT NULL,
    item_price_snapshot DECIMAL(19,2) NOT NULL,
    quantity INTEGER NOT NULL,

    CONSTRAINT fk_order_items_order
                        FOREIGN KEY (order_id)
                        REFERENCES orders(id),

    CONSTRAINT chk_order_item_price
                        CHECK(item_price_snapshot > 0),

    CONSTRAINT chk_order_item_quantity
                        CHECK(quantity >= 0),

    CONSTRAINT uk_order_items_order_item
                        UNIQUE (order_id, item_id)
);

CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);