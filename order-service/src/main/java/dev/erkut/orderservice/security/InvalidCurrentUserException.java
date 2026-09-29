package dev.erkut.orderservice.security;

public class InvalidCurrentUserException extends RuntimeException {

    public InvalidCurrentUserException(String message) {
        super(message);
    }
}