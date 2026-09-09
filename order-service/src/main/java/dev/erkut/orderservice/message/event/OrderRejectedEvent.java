package dev.erkut.orderservice.message.event;

import java.util.UUID;

public record OrderRejectedEvent(
    UUID orderId
) {}
