package dev.erkut.stockservice.outbox.domain;

public enum OutboxMessageType {
    STOCK_RESERVED_EVENT,
    STOCK_RESERVATION_FAILED_EVENT,
    STOCK_RESERVATION_CONFIRMED_EVENT,
    STOCK_RESERVATION_RELEASED_EVENT
}
