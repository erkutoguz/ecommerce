package dev.erkut.orderservice.cart.api.error;

import dev.erkut.orderservice.cart.api.CartController;
import dev.erkut.orderservice.cart.application.exception.CartNotFoundException;
import dev.erkut.orderservice.cart.domain.exception.CartItemNotFoundException;
import dev.erkut.orderservice.integration.customer.CustomerNotFoundException;
import dev.erkut.orderservice.integration.customer.CustomerServiceUnavailableException;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.InvalidProductStateException;
import dev.erkut.orderservice.integration.product.ProductNotFoundException;
import dev.erkut.orderservice.integration.product.ProductServiceUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = CartController.class)
public class CartExceptionHandler {

    @ExceptionHandler({
            CartNotFoundException.class,
            CartItemNotFoundException.class,
            ProductNotFoundException.class,
            CustomerNotFoundException.class
    })
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({
            InvalidCustomerStateException.class,
            InvalidProductStateException.class,
            IllegalStateException.class
    })
    public ResponseEntity<Map<String, String>> handleConflict(RuntimeException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        return error(HttpStatus.CONFLICT, "Cart was modified concurrently");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({
            CustomerServiceUnavailableException.class,
            ProductServiceUnavailableException.class
    })
    public ResponseEntity<Map<String, String>> handleUnavailable(RuntimeException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);

        return ResponseEntity.status(status).body(body);
    }
}