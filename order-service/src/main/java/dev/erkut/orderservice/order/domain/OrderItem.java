package dev.erkut.orderservice.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Entity
@Table(
        name = "order_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_order_items_order_product",
                columnNames = {"order_id", "product_id"}
        )
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "product_name_snapshot", nullable = false, length = 255)
    private String productNameSnapshot;

    @Column(
            name = "product_price_snapshot",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal productPriceSnapshot;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected OrderItem() {}

    private OrderItem(
            Order order,
            UUID productId,
            String productNameSnapshot,
            BigDecimal productPriceSnapshot,
            int quantity
    ) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }

        if (productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if (productNameSnapshot == null || productNameSnapshot.isBlank()) {
            throw new IllegalArgumentException("Product name cannot be blank");
        }

        if (productPriceSnapshot == null || productPriceSnapshot.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        this.order = order;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.productPriceSnapshot = validateMoney(productPriceSnapshot);
        this.quantity = quantity;
    }

    static OrderItem create(
            Order order,
            UUID productId,
            String productNameSnapshot,
            BigDecimal productPriceSnapshot,
            int quantity
    ) {
        return new OrderItem(
                order,
                productId,
                productNameSnapshot,
                productPriceSnapshot,
                quantity
        );
    }

    private static BigDecimal validateMoney(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }

        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Product price must have at most 2 decimal places");
        }
    }

    BigDecimal lineTotal() {
        return productPriceSnapshot.multiply(BigDecimal.valueOf(quantity));
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public BigDecimal getProductPriceSnapshot() {
        return productPriceSnapshot;
    }

    public int getQuantity() {
        return quantity;
    }
}
