package dev.erkut.orderservice.checkout.application.exception;

public class CartChangedDuringCheckoutException extends RuntimeException {
    public CartChangedDuringCheckoutException(String message) {
        super(message);
    }
}
