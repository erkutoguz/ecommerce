package dev.erkut.orderservice.integration.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record ProductLookupRequest(
        @NotEmpty
        List<@NotNull UUID> requestedProductIds
) {}
