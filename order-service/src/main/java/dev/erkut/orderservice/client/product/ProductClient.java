package dev.erkut.orderservice.client.product;

import dev.erkut.orderservice.exception.ProductNotFoundException;
import dev.erkut.orderservice.exception.ProductServiceUnavailableException;
import dev.erkut.orderservice.request.ProductClientLookupRequest;
import dev.erkut.orderservice.response.OrderProductResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.UUID;

@Component
public class ProductClient {

    private final RestClient client;

    public ProductClient(
            RestClient.Builder clientBuilder,
            @Value("${restclient.product.url}") String productUrl)
    {
        this.client = clientBuilder.baseUrl(productUrl).build();
    }

    public List<OrderProductResponse> getProductsByIds(ProductClientLookupRequest lookupReq) {
            try {
                return client.post()
                        .uri("/bulk-lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(lookupReq.requestedProductIds())
                        .retrieve()
                        .onStatus(
                                status -> status.value() == 404,
                                (req, res) -> {
                                    throw new ProductNotFoundException("Product(s) not found");
                        })
                        .onStatus(HttpStatusCode::is5xxServerError,
                                (req, res) -> {
                                    throw new ProductServiceUnavailableException("Product service unavailable");
                                })
                        .body(new ParameterizedTypeReference<List<OrderProductResponse>>() {});
            } catch (ResourceAccessException ex) {
                throw new ProductServiceUnavailableException("Product service unavailable", ex);
            }
    }

}
