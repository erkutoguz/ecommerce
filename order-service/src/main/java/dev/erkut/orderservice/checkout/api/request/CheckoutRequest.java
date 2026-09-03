package dev.erkut.orderservice.checkout.api.request;


import dev.erkut.orderservice.order.domain.Currency;
import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(
   @NotNull
   Currency currency
) {}
