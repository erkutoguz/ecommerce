package dev.erkut.paymentservice.outbox.application.exception;

public class OutboxSerializationException extends RuntimeException {
    public OutboxSerializationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
