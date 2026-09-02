package dev.erkut.orderservice.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderLineSnapshot(
        UUID productId,
        String productNameSnapshot,
        BigDecimal productPriceSnapshot,
        int quantity
) {}
