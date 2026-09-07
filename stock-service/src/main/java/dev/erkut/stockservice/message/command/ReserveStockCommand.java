package dev.erkut.stockservice.message.command;

import java.util.List;
import java.util.UUID;

public record ReserveStockCommand(
        UUID orderId,
        List<ReserveStockItem> items
) {
    public record ReserveStockItem(
            UUID productId,
            int quantity
    ) {}
}
