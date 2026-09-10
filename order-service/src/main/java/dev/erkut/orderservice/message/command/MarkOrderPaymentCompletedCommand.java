package dev.erkut.orderservice.message.command;

import java.util.UUID;

public record MarkOrderPaymentCompletedCommand(
        UUID orderId
) {}
