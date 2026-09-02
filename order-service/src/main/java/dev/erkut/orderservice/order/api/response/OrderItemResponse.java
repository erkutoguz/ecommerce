package dev.erkut.orderservice.order.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID itemId,
        String itemNameSnapshot,
        BigDecimal itemPriceSnapshot,
        Integer quantity
) {}
