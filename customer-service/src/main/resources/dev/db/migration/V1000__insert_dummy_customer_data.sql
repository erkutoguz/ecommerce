-- Development prerequisites for the API request smoke flows.
-- Customer, product and stock IDs are intentionally deterministic.
INSERT INTO customers (
    id, name, email, phone, status, updated_at, created_at
) VALUES
    ('c0000000-0000-0000-0000-000000000001', 'Happy Path Customer', 'customer@example.com', '+905550000001', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000002', 'Out Of Stock Customer', 'out-of-stock@example.com', '+905550000002', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('c0000000-0000-0000-0000-000000000003', 'Payment Expiry Customer', 'payment-expiry@example.com', '+905550000003', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO customer_addresses (
    id, customer_id, full_address, city, country
) VALUES
    ('a0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000001', 'Development Address 1', 'Istanbul', 'Turkey'),
    ('a0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000002', 'Development Address 2', 'Istanbul', 'Turkey'),
    ('a0000000-0000-0000-0000-000000000003', 'c0000000-0000-0000-0000-000000000003', 'Development Address 3', 'Istanbul', 'Turkey');
