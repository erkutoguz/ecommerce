package dev.erkut.orderservice.integration.customer;

import java.util.UUID;

public record CustomerLookupResponse (
        UUID customerId,
        CustomerStatus status
) {}
