package dev.erkut.orderworkflowservice.message.event.paymentevents;

import java.util.UUID;

public record PaymentFailedEvent(
   UUID orderId,
   PaymentFailureReason failureReason
) {}
