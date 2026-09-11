package dev.erkut.paymentservice.payment.api.response;

import dev.erkut.paymentservice.payment.domain.PaymentStatus;

import java.util.UUID;

public record PaymentResponse(
        UUID orderId,
        PaymentStatus status,
        String checkoutUrl
) {}
