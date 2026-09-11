ALTER TABLE stock_items
    ADD CONSTRAINT chk_stock_item_reserved_not_greater_than_on_hand
        CHECK (reserved_quantity <= on_hand_quantity);
