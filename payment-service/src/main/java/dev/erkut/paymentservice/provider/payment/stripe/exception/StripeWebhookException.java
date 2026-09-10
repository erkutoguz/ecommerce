package dev.erkut.paymentservice.provider.payment.stripe.exception;

public class StripeWebhookException extends RuntimeException {
    public StripeWebhookException(String message) {
        super(message);
    }

    public StripeWebhookException(String message, Throwable cause) {
        super(message, cause);
    }
}
