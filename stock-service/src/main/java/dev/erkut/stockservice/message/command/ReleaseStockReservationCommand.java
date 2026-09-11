package dev.erkut.stockservice.message.command;

import java.util.UUID;

public record ReleaseStockReservationCommand(
        UUID orderId
) {}
