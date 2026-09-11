-- Development prerequisites for the API request smoke flows.
-- Stock is seeded separately with the same product IDs in stock-service.
INSERT INTO products (
    id, name, price, status, updated_at, created_at
) VALUES
    ('d0000000-0000-0000-0000-000000000001', 'Mechanical Keyboard', 2499.90, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('d0000000-0000-0000-0000-000000000002', 'Wireless Mouse', 1299.90, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('d0000000-0000-0000-0000-000000000003', 'USB-C Dock', 3499.90, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
