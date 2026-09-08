package dev.erkut.orderworkflowservice.saga.domain.exception;

public class IllegalOrderSagaStateException extends RuntimeException {
    public IllegalOrderSagaStateException(String message) {
        super(message);
    }
}
