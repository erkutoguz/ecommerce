package dev.erkut.paymentservice.integration.order;

import java.util.UUID;

public record OrderOwnershipResponse(
        UUID orderId,
        UUID customerId
) {}
