package dev.erkut.orderservice.order.api.error;

import dev.erkut.orderservice.integration.customer.CustomerNotFoundException;
import dev.erkut.orderservice.integration.customer.CustomerServiceUnavailableException;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.order.api.OrderController;
import dev.erkut.orderservice.order.api.admin.AdminOrderController;
import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice(assignableTypes = {OrderController.class, AdminOrderController.class})
public class OrderExceptionHandler {

    @ExceptionHandler({
            OrderNotFoundException.class,
            CustomerNotFoundException.class
    })
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(InvalidCustomerStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCustomerState(InvalidCustomerStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(CustomerServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleCustomerServiceUnavailable(
            CustomerServiceUnavailableException ex
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    public ResponseEntity<Map<String, String>> handleInvalidOrderStateException(InvalidOrderStateException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        Map<String, String> body = new HashMap<>();
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
