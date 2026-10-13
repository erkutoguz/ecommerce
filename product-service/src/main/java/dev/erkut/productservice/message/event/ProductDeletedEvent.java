package dev.erkut.productservice.message.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record ProductDeletedEvent(UUID productId, long version) {
    @JsonCreator
    public ProductDeletedEvent(
            @JsonProperty("productId") UUID productId,
            @JsonProperty("version") Long version
    ) {
        this(productId, version == null ? 0L : version.longValue());
    }

    public ProductDeletedEvent(UUID productId) { this(productId, 0L); }
}
