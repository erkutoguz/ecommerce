    package dev.erkut.orderservice.exception;

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
    }
