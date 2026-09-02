package dev.erkut.productservice.product.api;

import dev.erkut.productservice.product.api.response.ProductResponse;
import dev.erkut.productservice.product.domain.Product;

public class ProductMapper {
    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStatus(),
                product.getUpdatedAt(),
                product.getCreatedAt());
    }
}
