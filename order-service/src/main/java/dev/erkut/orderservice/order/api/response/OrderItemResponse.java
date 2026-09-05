package dev.erkut.orderservice.order.api.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID productId,
        String productNameSnapshot,
        BigDecimal productPriceSnapshot,
        int quantity
) {}
