package dev.erkut.orderworkflowservice.message.command;

import java.util.UUID;

public record ConfirmStockReservationCommand(
    UUID orderId
) {}
