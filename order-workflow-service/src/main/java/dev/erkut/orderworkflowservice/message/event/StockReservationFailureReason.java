package dev.erkut.orderworkflowservice.message.event;

public enum StockReservationFailureReason {
    INSUFFICIENT_STOCK,
    ITEM_NOT_FOUND,
    ITEM_INACTIVE
}
