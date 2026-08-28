package dev.erkut.customerservice.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void illegalArgumentMapsToBadRequest() {
        var response = handler.handleIllegalArgumentException(new IllegalArgumentException("bad argument"));

        assertResponse(response, HttpStatus.BAD_REQUEST, "bad argument");
    }

    @Test
    void notFoundExceptionsMapToNotFound() {
        assertResponse(handler.handleCustomerNotFoundException(
                new CustomerNotFoundException("customer missing")), HttpStatus.NOT_FOUND, "customer missing");
        assertResponse(handler.handleAddressNotFoundException(
                new AddressNotFoundException("address missing")), HttpStatus.NOT_FOUND, "address missing");
    }

    @Test
    void customerConflictExceptionsMapToConflict() {
        assertResponse(handler.handleCustomerEmailAlreadyExistsException(
                new CustomerEmailAlreadyExistsException("duplicate email")), HttpStatus.CONFLICT, "duplicate email");
        assertResponse(handler.handleInvalidCustomerStateException(
                new InvalidCustomerStateException("inactive customer")), HttpStatus.CONFLICT, "inactive customer");
    }

    @Test
    void dataIntegrityViolationMapsToGenericConflict() {
        var response = handler.handleDataIntegrityViolationException(
                new DataIntegrityViolationException("constraint"));

        assertResponse(response, HttpStatus.CONFLICT, "Request conflicts with existing data");
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
