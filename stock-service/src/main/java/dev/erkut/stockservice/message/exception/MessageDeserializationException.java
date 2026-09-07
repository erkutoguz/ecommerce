package dev.erkut.stockservice.message.exception;

public class MessageDeserializationException extends RuntimeException {
    public MessageDeserializationException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
