package dev.erkut.searchservice.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.WriteTypeHint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Document(indexName = "products", createIndex = false, writeTypeHint = WriteTypeHint.FALSE)
public class ProductSearchDocument {

    @Id
    private UUID productId;
    private String name;
    private BigDecimal price;
    private ProductStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    protected ProductSearchDocument() {}

    private ProductSearchDocument(
            UUID productId,
            String name,
            BigDecimal price,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be null");
        }
        if(price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
        if (status == null) {
            throw new IllegalArgumentException("Product status cannot be null");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Product timestamps cannot be null");
        }
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ProductSearchDocument create(
            UUID productId,
            String name,
            BigDecimal price,
            ProductStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new ProductSearchDocument(productId, name, price, status, createdAt, updatedAt);
    }

    public UUID getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

}
