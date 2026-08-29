package dev.erkut.orderservice.exception;

public class InvalidProductStateException extends RuntimeException {
    public InvalidProductStateException(String message) {
        super(message);
    }
}
