package dev.erkut.orderservice.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID itemId,
        String itemNameSnapshot,
        BigDecimal itemPriceSnapshot,
        Integer quantity
) {}
