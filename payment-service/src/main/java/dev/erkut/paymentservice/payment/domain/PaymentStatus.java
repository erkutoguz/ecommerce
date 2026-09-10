package dev.erkut.paymentservice.payment.domain;

public enum PaymentStatus {
    PROCESSING,
    AWAITING_CUSTOMER_ACTION,
    COMPLETED,
    FAILED,
    UNKNOWN
}
