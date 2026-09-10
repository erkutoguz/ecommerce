package dev.erkut.orderworkflowservice.message.event;

import java.util.UUID;

public record StockReservationConfirmedEvent(
   UUID orderId
) {}
