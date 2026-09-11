ALTER TABLE orders
DROP CONSTRAINT chk_order_rejection_reason;

ALTER TABLE orders ADD CONSTRAINT chk_order_rejection_reason
    CHECK (rejection_reason IS NULL OR rejection_reason IN (
                                                            'OUT_OF_STOCK',
                                                            'PAYMENT_DECLINED',
                                                            'PAYMENT_EXPIRED',
                                                            'USER_CANCELLED',
                                                            'RESERVATION_EXPIRED'
        ));
