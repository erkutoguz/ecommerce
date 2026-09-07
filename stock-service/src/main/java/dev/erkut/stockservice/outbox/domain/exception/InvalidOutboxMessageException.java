package dev.erkut.stockservice.outbox.domain.exception;

public class InvalidOutboxMessageException extends RuntimeException {
    public InvalidOutboxMessageException(String message) {
        super(message);
    }
}
