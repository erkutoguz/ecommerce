package dev.erkut.orderservice.message.command;

import dev.erkut.orderservice.order.domain.OrderRejectionReason;

import java.util.UUID;

public record RejectOrderCommand(
        UUID orderId,
        OrderRejectionReason rejectionReason
) {}
