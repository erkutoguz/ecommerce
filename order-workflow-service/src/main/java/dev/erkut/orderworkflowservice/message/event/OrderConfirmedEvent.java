package dev.erkut.orderworkflowservice.message.event;

import java.util.UUID;

public record OrderConfirmedEvent(
   UUID orderId
) {}
