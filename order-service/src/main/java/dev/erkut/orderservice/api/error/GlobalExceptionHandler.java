package dev.erkut.orderservice.api.error;

import dev.erkut.orderservice.security.InvalidCurrentUserException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        return validationError();
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Map<String, String>> handleHandlerMethodValidationException(HandlerMethodValidationException ex) {
        return validationError();
    }

    @ExceptionHandler(InvalidCurrentUserException.class)
    public ResponseEntity<Map<String, String>> handleInvalidCurrentUser(InvalidCurrentUserException ex) {
        Map<String, String> body = new HashMap<>();
        body.put("error", "Authentication required");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body);
    }

    private static ResponseEntity<Map<String, String>> validationError() {
        Map<String, String> body = new HashMap<>();
        body.put("error", "Request validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }
}
