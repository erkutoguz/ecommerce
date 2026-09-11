package dev.erkut.orderworkflowservice.message.event.orderevents;

import java.util.UUID;

public record OrderRejectedEvent(
   UUID orderId
) {}
