package dev.erkut.paymentservice.security;

public class InvalidCurrentUserException extends RuntimeException {
    public InvalidCurrentUserException(String message) {
        super(message);
    }
}
