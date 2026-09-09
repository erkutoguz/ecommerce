package dev.erkut.orderworkflowservice.outbox.domain;

public enum OutboxMessageType {
    RESERVE_STOCK_COMMAND,
    REJECT_ORDER_COMMAND,
    INITIATE_PAYMENT_COMMAND
}
