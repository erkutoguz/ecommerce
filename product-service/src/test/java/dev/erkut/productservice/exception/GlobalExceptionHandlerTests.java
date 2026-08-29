package dev.erkut.productservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void productNotFoundMapsToNotFound() {
        assertResponse(handler.handleProductNotFoundException(
                new ProductNotFoundException("product missing")), HttpStatus.NOT_FOUND, "product missing");
    }

    @Test
    void invalidProductStateMapsToConflict() {
        assertResponse(handler.handleInvalidProductStateException(
                new InvalidProductStateException("inactive product")), HttpStatus.CONFLICT, "inactive product");
    }

    @Test
    void illegalArgumentMapsToBadRequest() {
        assertResponse(handler.handleIllegalArgumentException(
                new IllegalArgumentException("bad argument")), HttpStatus.BAD_REQUEST, "bad argument");
    }

    private static void assertResponse(
            org.springframework.http.ResponseEntity<java.util.Map<String, String>> response,
            HttpStatus status,
            String message
    ) {
        assertEquals(status, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(message, response.getBody().get("error"));
    }
}
