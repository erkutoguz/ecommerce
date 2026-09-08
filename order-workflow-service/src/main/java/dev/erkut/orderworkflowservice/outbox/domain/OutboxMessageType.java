package dev.erkut.orderworkflowservice.outbox.domain;

public enum OutboxMessageType {
    RESERVE_STOCK_COMMAND,
    REJECT_ORDER_COMMAND,
    PROCESS_PAYMENT_COMMAND
}
