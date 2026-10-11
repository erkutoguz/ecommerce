package dev.erkut.searchservice.api.response;

import dev.erkut.searchservice.document.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductSearchResponse(
        UUID productId,
        String name,
        BigDecimal price,
        ProductStatus status,
        Instant updatedAt,
        Instant createdAt
) {
}
