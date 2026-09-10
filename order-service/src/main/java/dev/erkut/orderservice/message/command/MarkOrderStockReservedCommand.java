package dev.erkut.orderservice.message.command;

import java.util.UUID;

public record MarkOrderStockReservedCommand(
        UUID orderId
) {}
