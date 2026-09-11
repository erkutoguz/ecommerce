package dev.erkut.orderworkflowservice.message.event.stockevents;

import java.util.UUID;

public record StockReservationConfirmedEvent(
   UUID orderId
) {}
