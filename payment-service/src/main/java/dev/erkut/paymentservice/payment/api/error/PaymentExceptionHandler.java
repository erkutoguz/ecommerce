package dev.erkut.paymentservice.payment.api.error;

import dev.erkut.paymentservice.payment.api.PaymentController;
import dev.erkut.paymentservice.payment.application.exception.PaymentNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice(assignableTypes = PaymentController.class)
public class PaymentExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<Map<String, String>> handlePaymentNotFound(PaymentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }
}
