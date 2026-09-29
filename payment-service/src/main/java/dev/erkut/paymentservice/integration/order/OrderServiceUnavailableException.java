package dev.erkut.paymentservice.integration.order;

public class OrderServiceUnavailableException extends RuntimeException {
    public OrderServiceUnavailableException(String message) {
        super(message);
    }

    public OrderServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
