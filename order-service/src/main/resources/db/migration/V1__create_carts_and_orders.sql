CREATE TABLE carts (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL,
    status          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_carts_status
        CHECK (status IN ('ACTIVE', 'CHECKOUT_LOCKED', 'COMPLETED'))
);

CREATE UNIQUE INDEX uk_carts_customer_open
    ON carts(customer_id)
    WHERE status IN ('ACTIVE', 'CHECKOUT_LOCKED');

CREATE INDEX idx_carts_customer_id
    ON carts(customer_id);

CREATE TABLE cart_items (
    id              UUID PRIMARY KEY,
    cart_id         UUID NOT NULL,
    product_id      UUID NOT NULL,
    quantity        INTEGER NOT NULL,

    CONSTRAINT fk_cart_items_cart
        FOREIGN KEY (cart_id)
        REFERENCES carts(id),

    CONSTRAINT chk_cart_item_quantity
        CHECK (quantity > 0),

    CONSTRAINT uk_cart_items_cart_product
        UNIQUE (cart_id, product_id)
);

CREATE INDEX idx_cart_items_cart_id
    ON cart_items(cart_id);

CREATE TABLE orders (
    id                  UUID PRIMARY KEY,
    source_cart_id      UUID NOT NULL,
    customer_id         UUID NOT NULL,
    status              VARCHAR(40) NOT NULL,
    rejection_reason    VARCHAR(40),
    currency            VARCHAR(3) NOT NULL,
    total_amount        DECIMAL(19, 2) NOT NULL,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL,
    confirmed_at        TIMESTAMP WITH TIME ZONE,
    rejected_at         TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_orders_source_cart
        FOREIGN KEY (source_cart_id)
        REFERENCES carts(id),

    CONSTRAINT chk_order_status
        CHECK (status IN (
            'PENDING_STOCK',
            'PENDING_PAYMENT',
            'PAYMENT_UNKNOWN',
            'PENDING_STOCK_CONFIRMATION',
            'CONFIRMED',
            'REJECTED'
        )
    ),

    CONSTRAINT chk_order_rejection_reason
        CHECK (rejection_reason IS NULL OR rejection_reason IN (
            'OUT_OF_STOCK',
            'PAYMENT_DECLINED',
            'USER_CANCELLED',
            'RESERVATION_EXPIRED'
        )
    ),

    CONSTRAINT chk_order_rejection_consistency
        CHECK ((status = 'REJECTED' AND rejection_reason IS NOT NULL)
        OR (status <> 'REJECTED' AND rejection_reason IS NULL)),

    CONSTRAINT chk_order_currency
        CHECK (currency IN ('TRY', 'EUR', 'USD')),

    CONSTRAINT chk_order_total_amount
        CHECK (total_amount > 0)
);

CREATE INDEX idx_orders_customer_id
    ON orders(customer_id);

CREATE INDEX idx_orders_source_cart_id
    ON orders(source_cart_id);

CREATE INDEX idx_orders_created_at
    ON orders(created_at);

CREATE TABLE order_items (
    id                      UUID PRIMARY KEY,
    order_id                UUID NOT NULL,
    product_id              UUID NOT NULL,
    product_name_snapshot   VARCHAR(255) NOT NULL,
    product_price_snapshot  DECIMAL(19, 2) NOT NULL,
    quantity                INTEGER NOT NULL,

    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id)
        REFERENCES orders(id),

    CONSTRAINT chk_order_product_price
        CHECK (product_price_snapshot > 0),

    CONSTRAINT chk_order_product_quantity
        CHECK (quantity > 0),

    CONSTRAINT uk_order_items_order_product
        UNIQUE (order_id, product_id)
);

CREATE INDEX idx_order_items_order_id
    ON order_items(order_id);
