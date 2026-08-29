package dev.erkut.orderservice.response;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderProductResponse(
        UUID productId,
        String name,
        BigDecimal price,
        ProductStatus status
) {}
