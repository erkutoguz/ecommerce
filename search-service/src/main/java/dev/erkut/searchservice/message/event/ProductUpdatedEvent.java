package dev.erkut.searchservice.message.event;

import dev.erkut.searchservice.document.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductUpdatedEvent(
        UUID productId,
        String name,
        BigDecimal price,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt
) {}
