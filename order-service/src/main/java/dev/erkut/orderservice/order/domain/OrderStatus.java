package dev.erkut.orderservice.order.domain;

public enum OrderStatus {
    PENDING_STOCK,
    PENDING_PAYMENT,
    PAYMENT_UNKNOWN,
    PENDING_STOCK_CONFIRMATION,
    CONFIRMED,
    REJECTED
}
