package dev.erkut.orderservice.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record ProductClientLookupRequest(
        @NotEmpty
        List<@NotNull UUID> requestedProductIds
) {}
