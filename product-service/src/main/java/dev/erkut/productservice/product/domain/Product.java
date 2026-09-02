package dev.erkut.productservice.product.domain;

import dev.erkut.productservice.product.domain.exception.InvalidProductStateException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "price", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProductStatus status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Product() {}

    private Product(String name, BigDecimal price, Instant now) {
        if(now == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.price = validatePrice(price);
        this.name = validateName(name);
        this.status = ProductStatus.ACTIVE;
        this.updatedAt = now;
        this.createdAt = now;
    }

    public static Product create(String name, BigDecimal price, Instant now) {
        return new Product(name, price, now);
    }

    public void changeProductName(String newName, Instant now) {
        ensureProductEditable();
        validateUpdateTime(now);
        this.name = validateName(newName);
        updateTime(now);
    }

    public void changeProductPrice(BigDecimal newPrice, Instant now) {
        ensureProductEditable();
        validateUpdateTime(now);
        this.price = validatePrice(newPrice);
        updateTime(now);
    }

    public void deactivateProduct(Instant now) {
        validateUpdateTime(now);
        if(status == ProductStatus.INACTIVE) {
            return;
        }
        status = ProductStatus.INACTIVE;
        updateTime(now);
    }

    private static BigDecimal validatePrice(BigDecimal value) {
        if(value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Product price must have at most 2 decimal places");
        }
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank");
        }

        if (name.length() > 255) {
            throw new IllegalArgumentException("Product name cannot exceed 255 characters");
        }

        return name;
    }

    private void ensureProductEditable() {
        if(this.status != ProductStatus.ACTIVE) {
            throw new InvalidProductStateException("Product cannot be modified with status " + status);
        }
    }

    private void updateTime(Instant now) {
        this.updatedAt = now;
    }

    private void validateUpdateTime(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Update time cannot be null");
        }
    }

    public UUID getId() {
        return id;
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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
