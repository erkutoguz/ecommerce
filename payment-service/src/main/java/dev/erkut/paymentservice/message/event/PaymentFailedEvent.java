package dev.erkut.paymentservice.message.event;

import java.util.UUID;

public record PaymentFailedEvent(
    UUID orderId,
    PaymentFailureReason failureReason
) {}
