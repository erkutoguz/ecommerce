package dev.erkut.orderworkflowservice.message.command.ordercommands;

import java.util.UUID;

public record RejectOrderCommand(
    UUID orderId,
    OrderRejectionReason rejectionReason
) {}
