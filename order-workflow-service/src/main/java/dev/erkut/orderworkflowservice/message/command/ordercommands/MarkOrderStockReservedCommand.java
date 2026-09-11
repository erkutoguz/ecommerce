package dev.erkut.orderworkflowservice.message.command.ordercommands;

import java.util.UUID;

public record MarkOrderStockReservedCommand(
        UUID orderId
) {}
