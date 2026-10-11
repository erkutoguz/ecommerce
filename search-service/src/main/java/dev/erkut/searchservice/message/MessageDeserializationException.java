package dev.erkut.searchservice.message;

public class MessageDeserializationException extends RuntimeException {
    public MessageDeserializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
