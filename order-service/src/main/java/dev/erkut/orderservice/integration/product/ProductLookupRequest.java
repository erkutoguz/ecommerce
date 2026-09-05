package dev.erkut.orderservice.integration.product;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collection;
import java.util.UUID;

public record ProductLookupRequest(
        @NotEmpty
        Collection<@NotNull UUID> requestedProductIds
) {}
