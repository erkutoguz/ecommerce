package dev.erkut.paymentservice.provider.payment;

public record PaymentSession(
        String providerPaymentId,
        String checkoutUrl
) {}