package dev.erkut.paymentservice.message.event;

import java.util.UUID;

public record PaymentCompletedEvent (
   UUID orderId
) {}
