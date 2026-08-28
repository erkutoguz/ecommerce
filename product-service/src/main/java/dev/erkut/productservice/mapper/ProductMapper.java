package dev.erkut.productservice.mapper;

import dev.erkut.productservice.dto.ProductResponse;
import dev.erkut.productservice.model.Product;

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
