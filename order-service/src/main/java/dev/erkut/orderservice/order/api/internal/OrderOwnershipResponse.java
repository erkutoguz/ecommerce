package dev.erkut.orderservice.order.api.internal;

import java.util.UUID;

public record OrderOwnershipResponse(
        UUID orderId,
        UUID customerId
) {}
