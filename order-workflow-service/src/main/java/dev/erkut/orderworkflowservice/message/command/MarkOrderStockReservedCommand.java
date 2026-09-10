package dev.erkut.orderworkflowservice.message.command;

import java.util.UUID;

public record MarkOrderStockReservedCommand(
        UUID orderId
) {}
