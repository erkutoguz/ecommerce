package dev.erkut.orderservice.api.error;

import dev.erkut.orderservice.security.InvalidCurrentUserException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidCurrentUser_shouldMapToUnauthorizedWithoutInternalDetails() {
        ResponseEntity<Map<String, String>> response = handler.handleInvalidCurrentUser(
                new InvalidCurrentUserException("JWT subject is not a valid UUID")
        );

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication required", response.getBody().get("error"));
    }
}
