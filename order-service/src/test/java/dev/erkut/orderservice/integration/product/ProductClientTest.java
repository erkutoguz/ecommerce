package dev.erkut.orderservice.integration.product;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withRawStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductClientTest {

    private static final String PRODUCT_SERVICE_URL = "http://product-service.test/products";
    private static final UUID FIRST_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID SECOND_PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000002");

    private MockRestServiceServer server;
    private ProductClient productClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        productClient = new ProductClient(builder, PRODUCT_SERVICE_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void bulkLookupPostsWrappedRequestAsJson() {
        server.expect(requestTo(PRODUCT_SERVICE_URL + "/bulk-lookup"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "requestedProductIds": [
                            "90000000-0000-0000-0000-000000000001",
                            "90000000-0000-0000-0000-000000000002"
                          ]
                        }
                        """))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        productClient.getProductsByIds(new ProductLookupRequest(
                List.of(FIRST_PRODUCT_ID, SECOND_PRODUCT_ID)));
    }

    @Test
    void successfulResponseIsDeserializedAndIgnoresExtraProductFields() {
        server.expect(requestTo(PRODUCT_SERVICE_URL + "/bulk-lookup"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("""
                        [
                          {
                            "productId": "90000000-0000-0000-0000-000000000001",
                            "name": "Mechanical Keyboard",
                            "price": 2500.00,
                            "status": "ACTIVE",
                            "createdAt": "2026-01-01T10:00:00Z",
                            "updatedAt": "2026-01-02T10:00:00Z"
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        List<ProductLookupResponse> response = productClient.getProductsByIds(
                new ProductLookupRequest(List.of(FIRST_PRODUCT_ID)));

        assertEquals(List.of(new ProductLookupResponse(
                FIRST_PRODUCT_ID,
                "Mechanical Keyboard",
                new BigDecimal("2500.00"),
                ProductStatus.ACTIVE)), response);
    }

    @Test
    void inactiveProductIsReturnedWithoutClientLevelFailure() {
        server.expect(requestTo(PRODUCT_SERVICE_URL + "/bulk-lookup"))
                .andRespond(withSuccess("""
                        [{
                          "productId": "90000000-0000-0000-0000-000000000001",
                          "name": "Discontinued Keyboard",
                          "price": 2500.00,
                          "status": "INACTIVE"
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<ProductLookupResponse> response = productClient.getProductsByIds(
                new ProductLookupRequest(List.of(FIRST_PRODUCT_ID)));

        assertEquals(ProductStatus.INACTIVE, response.getFirst().status());
    }

    @Test
    void notFoundResponseThrowsProductNotFoundException() {
        server.expect(requestTo(PRODUCT_SERVICE_URL + "/bulk-lookup"))
                .andRespond(withRawStatus(404));

        assertThrows(ProductNotFoundException.class, () -> productClient.getProductsByIds(
                new ProductLookupRequest(List.of(FIRST_PRODUCT_ID))));
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503})
    void anyServerErrorResponseThrowsProductServiceUnavailableException(int status) {
        server.expect(requestTo(PRODUCT_SERVICE_URL + "/bulk-lookup"))
                .andRespond(withRawStatus(status));

        assertThrows(ProductServiceUnavailableException.class, () -> productClient.getProductsByIds(
                new ProductLookupRequest(List.of(FIRST_PRODUCT_ID))));
    }

    @Test
    void lowLevelIoFailureThrowsProductServiceUnavailableException() {
        server.expect(requestTo(PRODUCT_SERVICE_URL + "/bulk-lookup"))
                .andRespond(withException(new IOException("connection failed")));

        ProductServiceUnavailableException exception = assertThrows(
                ProductServiceUnavailableException.class,
                () -> productClient.getProductsByIds(
                        new ProductLookupRequest(List.of(FIRST_PRODUCT_ID))));

        assertInstanceOf(ResourceAccessException.class, exception.getCause());
        assertInstanceOf(IOException.class, exception.getCause().getCause());
    }
}
