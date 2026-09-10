package dev.erkut.paymentservice.provider.payment.exception;

public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
