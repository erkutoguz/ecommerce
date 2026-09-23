ALTER TABLE customers
    ADD COLUMN auth_user_id UUID NOT NULL;

ALTER TABLE customers
    ALTER COLUMN name DROP NOT NULL;

ALTER TABLE customers
    ADD CONSTRAINT uk_customer_auth_user_id
        UNIQUE (auth_user_id);