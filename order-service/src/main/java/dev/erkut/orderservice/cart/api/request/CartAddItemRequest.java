package dev.erkut.orderservice.cart.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CartAddItemRequest(
        @NotNull
        UUID productId,

        @Positive
        int quantity
) {}
