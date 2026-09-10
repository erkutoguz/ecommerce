package dev.erkut.paymentservice.outbox.domain;

public enum OutboxMessageType {
    PAYMENT_COMPLETED_EVENT,
    PAYMENT_FAILED_EVENT
}
