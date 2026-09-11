package dev.erkut.orderworkflowservice.message.command.stockcommands;

import java.util.UUID;

public record ConfirmStockReservationCommand(
    UUID orderId
) {}
