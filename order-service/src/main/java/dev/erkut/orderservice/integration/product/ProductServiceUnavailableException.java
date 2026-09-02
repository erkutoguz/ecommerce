package dev.erkut.orderservice.integration.product;

public class ProductServiceUnavailableException extends RuntimeException {
    public ProductServiceUnavailableException(String message) {
        super(message);
    }
    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

}
