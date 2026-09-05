package dev.erkut.orderservice.cart.api.response;

import java.util.UUID;

public record CartItemResponse (
        UUID productId,
        int quantity
){}
