package dev.erkut.stockservice.reservation.application;

public enum ReservationProcessingOutcome {
    DUPLICATE,
    RESERVED,
    FAILED_INSUFFICIENT_STOCK,
    FAILED_ITEM_NOT_FOUND,
    FAILED_ITEM_INACTIVE,
    CONFIRMED,
    RELEASED
}
