package dev.erkut.paymentservice.provider.payment;

import dev.erkut.paymentservice.payment.domain.Currency;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProvider {
    PaymentSession initializePaymentSession(
            UUID orderId,
            BigDecimal totalAmount,
            Currency currency
    );
}
