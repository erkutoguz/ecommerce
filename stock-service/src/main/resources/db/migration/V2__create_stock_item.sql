CREATE TABLE stock_items
(
    product_id        UUID PRIMARY KEY,
    on_hand_quantity  int                      NOT NULL,
    reserved_quantity int                      NOT NULL,
    active            BOOLEAN                  NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT chk_stock_item_on_hand_quantity
        CHECK (on_hand_quantity >= 0),

    CONSTRAINT chk_stock_item_reserved_quantity
        CHECK (reserved_quantity >= 0)
);
