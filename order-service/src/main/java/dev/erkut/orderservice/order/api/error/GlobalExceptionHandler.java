package dev.erkut.orderservice.order.api.error;

import dev.erkut.orderservice.integration.customer.CustomerNotFoundException;
import dev.erkut.orderservice.integration.customer.CustomerServiceUnavailableException;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.InvalidProductStateException;
import dev.erkut.orderservice.integration.product.ProductNotFoundException;
import dev.erkut.orderservice.integration.product.ProductServiceUnavailableException;
import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import dev.erkut.orderservice.order.domain.exception.OrderItemNotFoundException;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgumentException(IllegalArgumentException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errors);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOrderStatusException(InvalidOrderStateException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errors);
    }

    @ExceptionHandler(OrderItemNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleItemNotFoundException(OrderItemNotFoundException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errors);
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleCustomerNotFoundException(CustomerNotFoundException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errors);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleProductNotFoundException(ProductNotFoundException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errors);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleOrderNotFoundException(OrderNotFoundException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errors);
    }

    @ExceptionHandler(CustomerServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleCustomerServiceUnavailableException(CustomerServiceUnavailableException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errors);
    }

    @ExceptionHandler(InvalidCustomerStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCustomerStateException(InvalidCustomerStateException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errors);
    }

    @ExceptionHandler(ProductServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleProductServiceUnavailableException(ProductServiceUnavailableException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(errors);
    }

    @ExceptionHandler(InvalidProductStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidProductStateException(InvalidProductStateException ex) {
        Map<String, String> errors = new HashMap<>();

        errors.put("error", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errors);
    }
}
