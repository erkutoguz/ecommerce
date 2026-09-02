package dev.erkut.orderservice.integration.customer;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class CustomerClient {
    private final RestClient client;

    public CustomerClient(RestClient.Builder clientBuilder,
                          @Value("${restclient.customer.url}") String customerUrl) {
        this.client = clientBuilder.baseUrl(customerUrl).build();
    }

    public CustomerLookupResponse getCustomerDetail(UUID customerId) {
        try {
            return this.client.get().uri("/{customerId}", customerId).retrieve()
                    .onStatus(
                            status -> status.value() == 404,
                            (req, res) -> {
                                throw new CustomerNotFoundException("Customer not found with id: " + customerId);
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
