package dev.erkut.orderservice.message;

public class MessageDeserializationException extends RuntimeException {
    public MessageDeserializationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
