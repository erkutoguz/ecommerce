package dev.erkut.productservice.message.event;

import dev.erkut.productservice.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductCreatedEvent(
        UUID productId,
        String name,
        BigDecimal price,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
