package dev.erkut.orderservice.cart.api.response;

import dev.erkut.orderservice.cart.domain.CartStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse (
        UUID id,
        UUID customerId,
        CartStatus status,
        List<CartItemResponse> cartItems,
        Instant updatedAt,
        Instant createdAt
) {}
