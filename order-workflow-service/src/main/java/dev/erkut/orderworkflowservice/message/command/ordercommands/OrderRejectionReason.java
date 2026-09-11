package dev.erkut.orderworkflowservice.message.command.ordercommands;

public enum OrderRejectionReason {
    OUT_OF_STOCK,
    PAYMENT_DECLINED,
    PAYMENT_EXPIRED,
    USER_CANCELLED,
    RESERVATION_EXPIRED
}
