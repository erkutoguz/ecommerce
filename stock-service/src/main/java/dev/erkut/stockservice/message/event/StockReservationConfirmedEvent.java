package dev.erkut.stockservice.message.event;

import java.util.UUID;

public record StockReservationConfirmedEvent(
   UUID orderId
) {}
