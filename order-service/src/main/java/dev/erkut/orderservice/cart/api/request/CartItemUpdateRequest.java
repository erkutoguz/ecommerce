package dev.erkut.orderservice.cart.api.request;

import jakarta.validation.constraints.Positive;

public record CartItemUpdateRequest(
   @Positive
   int quantity
) {}
