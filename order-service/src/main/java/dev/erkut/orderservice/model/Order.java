package dev.erkut.orderservice.model;

import dev.erkut.orderservice.exception.InvalidOrderStateException;
import dev.erkut.orderservice.exception.OrderItemNotFoundException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> items = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {}

    private Order(UUID customerId, Currency currency, Instant now) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id cannot be null");
        }

        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        if (now == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.customerId = customerId;
        this.currency = currency;
        this.status = OrderStatus.PENDING;
        this.totalAmount = BigDecimal.ZERO;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Order create(UUID customerId, Currency currency, Instant now) {
        return new Order(customerId, currency, now);
    }

    public void addItem(UUID itemId, String itemNameSnapshot, BigDecimal itemPriceSnapshot, int quantity, Instant now) {
        ensureOrderEditable();
        validateUpdateTime(now);

        if (itemId == null) {
            throw new IllegalArgumentException("Item id cannot be null");
        }

        OrderItem existingItem = items.stream()
                .filter(item -> item.hasItemId(itemId))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            existingItem.increaseQuantity(quantity);
            calculateTotalAmount();
            updateTime(now);
            return;
        }

        OrderItem newItem = OrderItem.create(
                this,
                itemId,
                itemNameSnapshot,
                itemPriceSnapshot,
                quantity);

        items.add(newItem);

        calculateTotalAmount();
        updateTime(now);
    }

    public void removeItem(UUID itemId, Instant now) {
        ensureOrderEditable();
        validateUpdateTime(now);

        OrderItem item = findItem(itemId);

        if (items.size() == 1) {
            throw new IllegalStateException("Order must contain at least one item");
        }

        items.remove(item);

        calculateTotalAmount();
        updateTime(now);
    }

    public void confirm(Instant now) {
        validateUpdateTime(now);

        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only pending orders can be confirmed");
        }

        ensureHasItems();

        status = OrderStatus.CONFIRMED;
        updateTime(now);
    }

    public void reject(Instant now) {
        validateUpdateTime(now);

        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Only pending orders can be rejected");
        }

        status = OrderStatus.REJECTED;
        updateTime(now);
    }

    public void cancel(Instant now) {
        validateUpdateTime(now);

        if (status == OrderStatus.CANCELLED) {
            return;
        }

        if (status == OrderStatus.REJECTED) {
            throw new InvalidOrderStateException("Rejected order cannot be cancelled");
        }

        status = OrderStatus.CANCELLED;
        updateTime(now);
    }

    public void changeItemQuantity(UUID itemId, int quantity, Instant now) {
        ensureOrderEditable();
        validateUpdateTime(now);

        OrderItem item = findItem(itemId);

        item.changeQuantity(quantity);

        calculateTotalAmount();
        updateTime(now);
    }

    private OrderItem findItem(UUID itemId) {
        if (itemId == null) {
            throw new IllegalArgumentException("Item id cannot be null");
        }

        return items.stream()
                .filter(item -> item.hasItemId(itemId))
                .findFirst()
                .orElseThrow(
                        () -> new OrderItemNotFoundException("Item does not exist in order: " + itemId)
                );
    }

    private void calculateTotalAmount() {
        this.totalAmount = items.stream()
                .map(OrderItem::calculateTotalAmount)
                .reduce(
                        BigDecimal.ZERO,
                        BigDecimal::add
                );
    }

    private void ensureOrderEditable() {
        if (status != OrderStatus.PENDING) {
            throw new InvalidOrderStateException("Order cannot be modified with status " + status);
        }
    }

    private void ensureHasItems() {
        if (items.isEmpty()) {
            throw new IllegalStateException("Order must contain at least one item");
        }
    }

    private void validateUpdateTime(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Update time cannot be null");
        }
    }

    private void updateTime(Instant now) {
        this.updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}