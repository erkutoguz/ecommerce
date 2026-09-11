package dev.erkut.orderworkflowservice.message.event.paymentevents;

import java.util.UUID;

public record PaymentCompletedEvent (
        UUID orderId
) {}
