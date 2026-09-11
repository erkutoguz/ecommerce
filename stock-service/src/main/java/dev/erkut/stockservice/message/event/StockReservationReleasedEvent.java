package dev.erkut.stockservice.message.event;

import java.util.UUID;

public record StockReservationReleasedEvent(
   UUID orderId
) {}
