package dev.erkut.productservice.outbox.domain;

public enum OutboxMessageType {
    PRODUCT_CREATED_EVENT,
    PRODUCT_UPDATED_EVENT,
    PRODUCT_DEACTIVATED_EVENT,
    PRODUCT_DELETED_EVENT
}
