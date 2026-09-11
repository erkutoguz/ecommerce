package dev.erkut.orderworkflowservice.message.event.stockevents;

import java.util.UUID;

public record StockReservationFailedEvent(
        UUID orderId,
        StockReservationFailureReason reason,
        UUID productId
) { }

