package dev.erkut.orderservice.order.api.error;

import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OrderExceptionHandlerTest {

    private final OrderExceptionHandler handler = new OrderExceptionHandler();

    @Test
    void orderNotFound_shouldMapToNotFound() {
        ResponseEntity<Map<String, String>> response = handler.handleOrderNotFoundException(
                new OrderNotFoundException("Order not found with id: 1")
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Order not found with id: 1", response.getBody().get("error"));
    }

    @Test
    void invalidOrderState_shouldMapToConflict() {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidOrderStateException(
                new InvalidOrderStateException("Cannot confirm order from status PENDING_STOCK")
        );

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Cannot confirm order from status PENDING_STOCK", response.getBody().get("error"));
    }

    @Test
    void illegalArgument_shouldMapToBadRequest() {
        ResponseEntity<Map<String, String>> response = handler.handleBadRequest(
                        new IllegalArgumentException("Rejection reason cannot be null")
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Rejection reason cannot be null", response.getBody().get("error"));
    }

}
