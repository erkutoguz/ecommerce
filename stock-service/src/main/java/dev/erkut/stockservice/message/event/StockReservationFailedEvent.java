package dev.erkut.stockservice.message.event;

import dev.erkut.stockservice.reservation.domain.StockReservationFailureReason;

import java.util.UUID;

public record StockReservationFailedEvent(
        UUID orderId,
        StockReservationFailureReason reason,
        UUID productId
) { }
