package dev.erkut.orderservice.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "item_name_snapshot", nullable = false, length = 255)
    private String itemNameSnapshot;

    @Column(
            name = "item_price_snapshot",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal itemPriceSnapshot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    protected OrderItem() {}

    private OrderItem(
            Order order,
            UUID itemId,
            String itemNameSnapshot,
            BigDecimal itemPriceSnapshot,
            int quantity
    ) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (itemId == null) {
            throw new IllegalArgumentException("Item id cannot be null");
        }

        if (itemNameSnapshot == null || itemNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("Item name cannot be blank");
        }

        if (itemPriceSnapshot == null || itemPriceSnapshot.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Item price must be greater than zero");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        this.order = order;
        this.itemId = itemId;
        this.itemNameSnapshot = itemNameSnapshot;
        this.itemPriceSnapshot = validateMoney(itemPriceSnapshot);
        this.quantity = quantity;
    }

    static OrderItem create(
            Order order,
            UUID itemId,
            String itemNameSnapshot,
            BigDecimal itemPriceSnapshot,
            int quantity
    ) {
        return new OrderItem(
                order,
                itemId,
                itemNameSnapshot,
                itemPriceSnapshot,
                quantity
        );
    }

    void changeQuantity(int quantity) {
        if(quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        this.quantity = quantity;
    }

    void increaseQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Increase amount must be greater than zero");
        }

        this.quantity += amount;
    }

    private static BigDecimal validateMoney(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Item price must be greater than zero");
        }

        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Item price must have at most 2 decimal places");
        }
    }

    BigDecimal calculateTotalAmount() {
        return itemPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    boolean hasItemId(UUID itemId) {
        return this.itemId.equals(itemId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getItemId() {
        return itemId;
    }

    public String getItemNameSnapshot() {
        return itemNameSnapshot;
    }

    public BigDecimal getItemPriceSnapshot() {
        return itemPriceSnapshot;
    }

    public Integer getQuantity() {
        return quantity;
    }
}