package dev.erkut.customerservice.customer.api.internal;

import dev.erkut.customerservice.customer.domain.CustomerStatus;

import java.util.UUID;

public record CustomerLookupResponse(
   UUID customerId,
   CustomerStatus status
) {}
