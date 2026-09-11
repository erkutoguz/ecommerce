package dev.erkut.orderworkflowservice.message.event.stockevents;

import java.util.UUID;

public record StockReservationReleasedEvent(
        UUID orderId
) {}
