package dev.erkut.orderservice.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record OrderItemRequest(
   @NotNull
   UUID itemId,

   @NotNull
   @Positive
   Integer quantity
) {}
