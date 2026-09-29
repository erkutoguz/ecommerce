package dev.erkut.paymentservice.integration.order;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OrderOwnershipClientTest {

    private static final String ORDER_SERVICE_BASE_URL = "http://order-service.test";
    private static final UUID ORDER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String ACCESS_TOKEN = "access-token";

    private MockRestServiceServer server;
    private OrderOwnershipClient orderOwnershipClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        orderOwnershipClient = new OrderOwnershipClient(builder, ORDER_SERVICE_BASE_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void successfulResponseForwardsBearerTokenAndReturnsOwnership() {
        server.expect(requestTo(ownershipUrl()))
                .andExpect(method(HttpMethod.GET))
                .andExpect(request -> assertEquals(
                        "Bearer " + ACCESS_TOKEN,
                        request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION)))
                .andRespond(withSuccess(
                        "{\"orderId\":\"" + ORDER_ID + "\",\"customerId\":\"" + CUSTOMER_ID + "\"}",
                        MediaType.APPLICATION_JSON));

        OrderOwnershipResponse response = orderOwnershipClient.getOwnership(ORDER_ID, ACCESS_TOKEN);

        assertEquals(ORDER_ID, response.orderId());
        assertEquals(CUSTOMER_ID, response.customerId());
    }

    @Test
    void notFoundResponseThrowsOrderNotFoundException() {
        server.expect(requestTo(ownershipUrl()))
                .andRespond(withRawStatus(404));

        assertThrows(
                OrderNotFoundException.class,
                () -> orderOwnershipClient.getOwnership(ORDER_ID, ACCESS_TOKEN)
        );
    }

    private static String ownershipUrl() {
        return ORDER_SERVICE_BASE_URL + "/internal/orders/" + ORDER_ID + "/ownership";
    }
}
