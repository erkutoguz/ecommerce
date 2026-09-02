package dev.erkut.customerservice.customer.api.response;

import dev.erkut.customerservice.customer.domain.CustomerStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerResponse(
    UUID customerId,
    String name,
    String email,
    String phone,
    CustomerStatus status,
    List<CustomerAddressResponse> addresses,
    Instant createdAt,
    Instant updatedAt
){}
