package dev.erkut.orderworkflowservice.message.event;

import java.util.UUID;

public record StockReservationFailedEvent(
        UUID orderId,
        StockReservationFailureReason reason,
        UUID productId
) { }

