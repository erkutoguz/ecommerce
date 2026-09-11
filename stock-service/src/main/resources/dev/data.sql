-- Product seed IDs must match product-service/src/main/resources/dev/db/migration/V1000__insert_dummy_product_data.sql.
INSERT INTO stock_items (
    product_id,
    on_hand_quantity,
    reserved_quantity,
    active,
    updated_at,
    created_at
) VALUES
    ('d0000000-0000-0000-0000-000000000001', 100, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('d0000000-0000-0000-0000-000000000002', 50, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('d0000000-0000-0000-0000-000000000003', 0, 0, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (product_id) DO NOTHING;
