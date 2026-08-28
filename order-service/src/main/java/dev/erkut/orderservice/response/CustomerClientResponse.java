package dev.erkut.orderservice.response;

import dev.erkut.orderservice.model.CustomerStatus;

import java.util.UUID;

public record CustomerClientResponse (
        UUID customerId,
        CustomerStatus status
) {}
