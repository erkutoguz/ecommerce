package dev.erkut.orderservice.dto;

import dev.erkut.orderservice.model.Currency;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record OrderCreateRequest(
        @NotNull
        UUID customerId,

        @NotNull
        Currency currency,

        @NotEmpty
        @Valid
        List<OrderItemCreateRequest> items
) {}
