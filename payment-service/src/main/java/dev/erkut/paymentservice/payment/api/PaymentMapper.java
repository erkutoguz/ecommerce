package dev.erkut.paymentservice.payment.api;

import dev.erkut.paymentservice.payment.api.response.PaymentResponse;
import dev.erkut.paymentservice.payment.domain.Payment;

public final class PaymentMapper {

    private PaymentMapper() {}

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getOrderId(),
                payment.getStatus(),
                payment.getCheckoutUrl()
        );
    }
}
