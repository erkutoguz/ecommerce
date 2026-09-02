package dev.erkut.orderservice.order.api.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemUpdateRequest(
   @NotNull
   @Positive
   Integer quantity
) {}
