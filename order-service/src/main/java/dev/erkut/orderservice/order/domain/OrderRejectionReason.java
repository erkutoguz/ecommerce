package dev.erkut.orderservice.order.domain;

public enum OrderRejectionReason {
    OUT_OF_STOCK,
    PAYMENT_DECLINED,
    USER_CANCELLED,
    RESERVATION_EXPIRED
}
