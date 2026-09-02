package dev.erkut.orderservice.integration.customer;

public class CustomerServiceUnavailableException extends RuntimeException {
    public CustomerServiceUnavailableException(String message) {
        super(message);
    }
    public CustomerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
