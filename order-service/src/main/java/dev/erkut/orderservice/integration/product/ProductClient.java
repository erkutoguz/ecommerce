package dev.erkut.orderservice.integration.product;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@Component
public class ProductClient {

    private final RestClient client;

    public ProductClient(
            RestClient.Builder clientBuilder,
            @Value("${restclient.product.url}") String productUrl)
    {
        this.client = clientBuilder.baseUrl(productUrl).build();
    }

    public List<ProductLookupResponse> getProductsByIds(ProductLookupRequest lookupReq) {
            try {
                return client.post()
                        .uri("/bulk-lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(lookupReq)
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
                        .body(new ParameterizedTypeReference<List<ProductLookupResponse>>() {});
            } catch (ResourceAccessException ex) {
                throw new ProductServiceUnavailableException("Product service unavailable", ex);
            }
    }

}
