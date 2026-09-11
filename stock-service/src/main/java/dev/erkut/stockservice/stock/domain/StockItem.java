package dev.erkut.stockservice.stock.domain;

import dev.erkut.stockservice.stock.domain.exception.InactiveStockItemException;
import dev.erkut.stockservice.stock.domain.exception.InsufficientStockException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "active")
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockItem() {}

    private StockItem(UUID productId, Instant createdAt) {
        if(productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if(createdAt == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.productId = productId;
        this.onHandQuantity = 0;
        this.reservedQuantity = 0;
        this.active = true;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public static StockItem create(UUID productId, Instant createdAt) {
        return new StockItem(productId, createdAt);
    }

    public UUID getProductId() {
        return productId;
    }

    public int getOnHandQuantity() {
        return onHandQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public void deactivate() {
        this.active = false;
    }

    public void validateReservation(int quantity) {
        if (quantity <= 0) {
            throw new IllegalStateException("Quantity must be greater than zero");
        }

        if (!active) {
            throw new InactiveStockItemException("Item is not active", productId);
        }

        int availableQuantity = onHandQuantity - reservedQuantity;

        if (availableQuantity < quantity) {
            throw new InsufficientStockException("Insufficient stock", productId);
        }
    }

    public void reserve(int quantity) {
        validateReservation(quantity);
        reservedQuantity += quantity;
    }

    public void confirm(int quantity) {
        validateConfirm(quantity);

        reservedQuantity -= quantity;
        onHandQuantity -= quantity;
    }

    public void validateConfirm(int quantity) {
        validatePositiveQuantity(quantity);

        if (quantity > reservedQuantity) {
            throw new InsufficientStockException(
                    "Insufficient reserved stock",
                    productId
            );
        }

        if (quantity > onHandQuantity) {
            throw new InsufficientStockException(
                    "Insufficient on-hand stock",
                    productId
            );
        }
    }

    public void release(int quantity) {
        validateRelease(quantity);

        reservedQuantity -= quantity;
    }

    public void validateRelease(int quantity) {
        validatePositiveQuantity(quantity);

        if (quantity > reservedQuantity) {
            throw new InsufficientStockException(
                    "Insufficient reserved stock",
                    productId
            );
        }
    }

    private void validatePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalStateException("Quantity must be greater than zero");
        }
    }


    public boolean isActive() {
        return active;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
