package dev.erkut.orderservice.integration.product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductLookupResponse(
        UUID productId,
        String name,
        BigDecimal price,
        ProductStatus status
) {}
