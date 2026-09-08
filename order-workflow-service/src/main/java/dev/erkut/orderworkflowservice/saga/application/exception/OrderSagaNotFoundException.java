package dev.erkut.orderworkflowservice.saga.application.exception;

public class OrderSagaNotFoundException extends RuntimeException {
    public OrderSagaNotFoundException(String message) {
        super(message);
    }
}
