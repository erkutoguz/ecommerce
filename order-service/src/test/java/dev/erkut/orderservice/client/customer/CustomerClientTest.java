package dev.erkut.orderservice.client.customer;

import dev.erkut.orderservice.exception.CustomerNotFoundException;
import dev.erkut.orderservice.exception.CustomerServiceUnavailableException;
import dev.erkut.orderservice.model.CustomerStatus;
import dev.erkut.orderservice.response.CustomerClientResponse;
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

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class CustomerClientTest {

    private static final String CUSTOMER_SERVICE_URL = "http://customer-service.test/customers";
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private MockRestServiceServer server;
    private CustomerClient customerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        customerClient = new CustomerClient(builder, CUSTOMER_SERVICE_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void successfulResponseIsDeserializedIntoCustomerClientResponse() {
        server.expect(requestTo(customerUrl()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"customerId\":\"" + CUSTOMER_ID + "\",\"status\":\"ACTIVE\"}",
                        MediaType.APPLICATION_JSON));

        CustomerClientResponse response = customerClient.getCustomerDetail(CUSTOMER_ID);

        assertEquals(CUSTOMER_ID, response.customerId());
        assertEquals(CustomerStatus.ACTIVE, response.status());
    }

    @Test
    void notFoundResponseThrowsCustomerNotFoundException() {
        server.expect(requestTo(customerUrl()))
                .andRespond(withRawStatus(404));

        CustomerNotFoundException exception = assertThrows(
                CustomerNotFoundException.class,
                () -> customerClient.getCustomerDetail(CUSTOMER_ID));

        assertEquals("Customer not found with id: " + CUSTOMER_ID, exception.getMessage());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void anyServerErrorResponseThrowsCustomerServiceUnavailableException(int status) {
        server.expect(requestTo(customerUrl()))
                .andRespond(withRawStatus(status));

        CustomerServiceUnavailableException exception = assertThrows(
                CustomerServiceUnavailableException.class,
                () -> customerClient.getCustomerDetail(CUSTOMER_ID));

        assertEquals("Customer service unavailable", exception.getMessage());
    }

    @Test
    void lowLevelIoFailureThrowsCustomerServiceUnavailableException() {
        server.expect(requestTo(customerUrl()))
                .andRespond(withException(new IOException("connection failed")));

        CustomerServiceUnavailableException exception = assertThrows(
                CustomerServiceUnavailableException.class,
                () -> customerClient.getCustomerDetail(CUSTOMER_ID));

        assertEquals("Customer service unavailable", exception.getMessage());
        assertInstanceOf(ResourceAccessException.class, exception.getCause());
        assertInstanceOf(IOException.class, exception.getCause().getCause());
    }

    private static String customerUrl() {
        return CUSTOMER_SERVICE_URL + "/" + CUSTOMER_ID;
    }
}
