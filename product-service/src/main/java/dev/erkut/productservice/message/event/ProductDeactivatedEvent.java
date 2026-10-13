package dev.erkut.productservice.message.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.erkut.productservice.product.domain.ProductStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductDeactivatedEvent(
   UUID productId,
   String name,
   BigDecimal price,
   ProductStatus status,
   Instant createdAt,
   Instant updatedAt,
   long version
) {
    @JsonCreator
    public ProductDeactivatedEvent(
            @JsonProperty("productId") UUID productId,
            @JsonProperty("name") String name,
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("status") ProductStatus status,
            @JsonProperty("createdAt") Instant createdAt,
            @JsonProperty("updatedAt") Instant updatedAt,
            @JsonProperty("version") Long version
    ) {
        this(productId, name, price, status, createdAt, updatedAt, version == null ? 0L : version.longValue());
    }

    public ProductDeactivatedEvent(UUID productId, String name, BigDecimal price, ProductStatus status,
                                   Instant createdAt, Instant updatedAt) {
        this(productId, name, price, status, createdAt, updatedAt, 0L);
    }
}
