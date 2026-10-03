package dev.erkut.orderservice.integration.customer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CustomerClient {
    private final RestClient client;

    public CustomerClient(RestClient.Builder clientBuilder,
                          @Value("${restclient.customer.base-url}") String customerBaseUrl) {
        this.client = clientBuilder.baseUrl(customerBaseUrl).build();
    }

    public CustomerLookupResponse getCurrentCustomer(String accessToken) {
        try {
            return this.client.get()
                    .uri("/internal/customer")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(
                            status -> status.value() == 404,
                            (req, res) -> {
                                throw new CustomerNotFoundException(
                                        "Customer not found"
                                );
                            })
                    .onStatus(HttpStatusCode::is4xxClientError,
                            (req, res) -> {
                                throw new CustomerServiceUnavailableException(
                                        "Customer service returned unexpected status: " + res.getStatusCode().value()
                                );
                            })
                    .onStatus(HttpStatusCode::is5xxServerError,
                            (req, res) -> {
                                throw new CustomerServiceUnavailableException("Customer service unavailable");
                            })
                    .body(CustomerLookupResponse.class);
        } catch (ResourceAccessException ex) {
            throw new CustomerServiceUnavailableException("Customer service unavailable", ex);
        }
    }
}
