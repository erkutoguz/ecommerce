package dev.erkut.paymentservice.integration.order;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class OrderOwnershipClient {
    private final RestClient client;

    public OrderOwnershipClient(
            RestClient.Builder clientBuilder,
            @Value("${restclient.order.base-url}") String orderBaseUrl
    ) {
        this.client = clientBuilder.baseUrl(orderBaseUrl).build();
    }

    public OrderOwnershipResponse getOwnership(UUID orderId, String accessToken) {
        try {
            return client.get()
                    .uri("/internal/orders/{orderId}/ownership", orderId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.value() == 404,
                            (req, res) -> {
                                throw new OrderNotFoundException("Order not found: " + orderId);
                            })
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (req, res) -> {
                                throw new OrderServiceUnavailableException(
                                        "Order service returned unexpected status: " + res.getStatusCode().value()
                                );
                            })
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (req, res) -> {
                                throw new OrderServiceUnavailableException("Order service unavailable");
                            })
                    .body(OrderOwnershipResponse.class);
        } catch (ResourceAccessException exception) {
            throw new OrderServiceUnavailableException("Order service unavailable", exception);
        }
    }
}
