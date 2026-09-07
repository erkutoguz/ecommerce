package dev.erkut.stockservice.stock.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @Column(name = "product_id")
    private UUID productId;

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
