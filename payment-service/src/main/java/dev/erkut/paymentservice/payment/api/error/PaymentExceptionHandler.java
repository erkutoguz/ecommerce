package dev.erkut.paymentservice.payment.api.error;

import dev.erkut.paymentservice.payment.api.PaymentController;
import dev.erkut.paymentservice.integration.order.OrderNotFoundException;
import dev.erkut.paymentservice.integration.order.OrderServiceUnavailableException;
import dev.erkut.paymentservice.payment.application.exception.PaymentNotFoundException;
import dev.erkut.paymentservice.security.InvalidCurrentUserException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    @ExceptionHandler({
            PaymentNotFoundException.class,
            OrderNotFoundException.class
    })
    public ResponseEntity<Map<String, String>> handleNotFound(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCurrentUserException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCurrentUser() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
    }

    @ExceptionHandler(OrderServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleUnavailable(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", exception.getMessage()));
    }
}
