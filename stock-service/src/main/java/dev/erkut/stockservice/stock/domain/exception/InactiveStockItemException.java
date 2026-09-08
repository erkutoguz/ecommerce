package dev.erkut.stockservice.stock.domain.exception;

import java.util.UUID;

public class InactiveStockItemException extends RuntimeException {
    private UUID productId;

    public InactiveStockItemException(String message, UUID productId) {
        super(message);
        this.productId = productId;
    }

    public UUID getProductId() { return productId; }
}
