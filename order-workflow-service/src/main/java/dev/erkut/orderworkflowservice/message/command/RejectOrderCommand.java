package dev.erkut.orderworkflowservice.message.command;

import java.util.UUID;

public record RejectOrderCommand(
    UUID orderId,
    OrderRejectionReason rejectionReason
) {}
