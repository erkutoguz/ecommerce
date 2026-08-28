package dev.erkut.orderservice.exception;

public class InvalidCustomerStateException extends RuntimeException {
    public InvalidCustomerStateException(String message) {
        super(message);
    }
}
