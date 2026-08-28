CREATE TABLE products (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    price DECIMAL(19,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,

    CONSTRAINT chk_product_price
                      CHECK (price > 0),

    CONSTRAINT chk_product_status
                      CHECK (status IN ('ACTIVE', 'INACTIVE'))
);
