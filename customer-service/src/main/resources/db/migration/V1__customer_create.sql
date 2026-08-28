CREATE TABLE customers (
    id          UUID PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    phone       VARCHAR(20),
    status      VARCHAR(20) NOT NULL,
    updated_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL,

    CONSTRAINT uk_customer_email UNIQUE (email),

    CONSTRAINT chk_customer_status
                       CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_customer_created_at
    ON customers(created_at);

CREATE TABLE customer_addresses (
    id              UUID PRIMARY KEY,
    customer_id     UUID NOT NULL,
    full_address    VARCHAR(255) NOT NULL,
    city            VARCHAR(50) NOT NULL,
    country         VARCHAR(50) NOT NULL,

    CONSTRAINT fk_customer_address_customer_id
                                FOREIGN KEY (customer_id)
                                REFERENCES customers(id)
);

CREATE INDEX idx_customer_address_customer_id
    ON customer_addresses(customer_id);
