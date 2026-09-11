package dev.erkut.paymentservice.payment.application;

public final class PaymentFailureReasonMapper {
    private PaymentFailureReasonMapper() {
    }

    public static dev.erkut.paymentservice.message.event.PaymentFailureReason toEvent(
            PaymentFailureReason reason
    ) {
        if (reason == null) {
            throw new IllegalArgumentException("Payment failure reason cannot be null");
        }
        return switch (reason) {
            case DECLINED ->
                    throw new IllegalArgumentException(
                            "DECLINED is not a supported terminal payment wire failure reason"
                    );
            case EXPIRED ->
                    dev.erkut.paymentservice.message.event.PaymentFailureReason.SESSION_EXPIRED;

        };
    }
}
