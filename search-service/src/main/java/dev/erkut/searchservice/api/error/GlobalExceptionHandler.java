package dev.erkut.searchservice.api.error;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<Map<String, String>> handleValidationException(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "Request validation failed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleMethodArgumentTypeMismatch(
            MethodArgumentTypeMismatchException exception
    ) {
        return error(HttpStatus.BAD_REQUEST, "Invalid request parameter: " + exception.getName());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(DataAccessResourceFailureException.class)
    public ResponseEntity<Map<String, String>> handleElasticsearchUnavailable(
            DataAccessResourceFailureException exception
    ) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "Search service is temporarily unavailable");
    }

    @ExceptionHandler(UncategorizedElasticsearchException.class)
    public ResponseEntity<Map<String, String>> handleElasticsearchException(
            UncategorizedElasticsearchException exception
    ) {
        Integer statusCode = exception.getStatusCode();
        if (statusCode != null && (statusCode == 502 || statusCode == 503)) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, "Search service is temporarily unavailable");
        }
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Search request failed");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleUnexpectedException(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "Search request failed");
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
