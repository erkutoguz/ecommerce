package dev.erkut.orderservice.outbox.domain;

public enum OutboxStatus {
    PENDING,
    IN_PROGRESS,
    PUBLISHED
}
