package dev.erkut.orderservice.integration.customer;

import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomerClientTest {

    private static final String CUSTOMER_SERVICE_BASE_URL = "http://customer-service.test";
    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private MockRestServiceServer server;
    private CustomerClient customerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        customerClient = new CustomerClient(builder, CUSTOMER_SERVICE_BASE_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void successfulResponseIsDeserializedIntoCustomerLookupResponse() {
        server.expect(requestTo(internalCustomerUrl()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"customerId\":\"" + CUSTOMER_ID + "\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON));

        CustomerLookupResponse response = customerClient.getByAuthUserId(AUTH_USER_ID);

        assertEquals(CUSTOMER_ID, response.customerId());
        assertEquals(CustomerStatus.ACTIVE, response.status());
    }

    @Test
    void notFoundResponseThrowsCustomerNotFoundException() {
        server.expect(requestTo(internalCustomerUrl()))
                .andRespond(withRawStatus(404));

        CustomerNotFoundException exception = assertThrows(
                CustomerNotFoundException.class,
                () -> customerClient.getByAuthUserId(AUTH_USER_ID));

        assertEquals("Customer not found for auth user: " + AUTH_USER_ID, exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void anyServerErrorResponseThrowsCustomerServiceUnavailableException(int status) {
        server.expect(requestTo(internalCustomerUrl()))
                .andRespond(withRawStatus(status));

        CustomerServiceUnavailableException exception = assertThrows(
                CustomerServiceUnavailableException.class,
                () -> customerClient.getByAuthUserId(AUTH_USER_ID));

        assertEquals("Customer service unavailable", exception.getMessage());
    }

    @Test
    void lowLevelIoFailureThrowsCustomerServiceUnavailableException() {
        server.expect(requestTo(internalCustomerUrl()))
                .andRespond(withException(new IOException("connection failed")));

        CustomerServiceUnavailableException exception = assertThrows(
                CustomerServiceUnavailableException.class,
                () -> customerClient.getByAuthUserId(AUTH_USER_ID));

        assertEquals("Customer service unavailable", exception.getMessage());
        assertInstanceOf(ResourceAccessException.class, exception.getCause());
        assertInstanceOf(IOException.class, exception.getCause().getCause());
    }

    @Test
    void unexpectedClientErrorThrowsCustomerServiceUnavailableException() {
        server.expect(requestTo(internalCustomerUrl()))
                .andRespond(withRawStatus(400));

        CustomerServiceUnavailableException exception = assertThrows(
                CustomerServiceUnavailableException.class,
                () -> customerClient.getByAuthUserId(AUTH_USER_ID));

        assertEquals("Customer service returned unexpected status: 400", exception.getMessage());
    }

    private static String internalCustomerUrl() {
        return CUSTOMER_SERVICE_BASE_URL + "/internal/by-auth-user/" + AUTH_USER_ID;
    }
}
