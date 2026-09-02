package dev.erkut.orderservice.order.domain;

import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "source_cart_id", nullable = false)
    private UUID sourceCartId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<OrderItem> orderItems = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_reason", length = 40)
    private OrderRejectionReason rejectionReason;

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

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    protected Order() {}

    private Order(
            UUID sourceCartId,
            UUID customerId,
            Currency currency,
            List<OrderLineSnapshot> itemSnapshots,
            Instant now
    ) {
        if (sourceCartId == null) {
            throw new IllegalArgumentException("Source cart id cannot be null");
        }

        if (customerId == null) {
            throw new IllegalArgumentException("Customer id cannot be null");
        }

        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        if (now == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        if (itemSnapshots == null || itemSnapshots.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one item");
        }

        this.sourceCartId = sourceCartId;
        this.customerId = customerId;
        this.currency = currency;
        this.status = OrderStatus.PENDING_STOCK;
        this.rejectionReason = null;
        this.createdAt = now;
        this.updatedAt = now;
        this.confirmedAt = null;
        this.rejectedAt = null;

        addSnapshots(itemSnapshots);
        this.totalAmount = calculateTotalAmount();
    }

    public static Order create(
            UUID sourceCartId,
            UUID customerId,
            Currency currency,
            List<OrderLineSnapshot> itemSnapshots,
            Instant now
    ) {
        return new Order(sourceCartId, customerId, currency, itemSnapshots, now);
    }

    public void markStockReserved(Instant now) {
        ensureNow(now);
        if (status != OrderStatus.PENDING_STOCK) {
            throw new InvalidOrderStateException("Cannot mark stock reserved from status " + status);
        }

        this.status = OrderStatus.PENDING_PAYMENT;
        this.updatedAt = now;
    }

    public void markPaymentUnknown(Instant now) {
        ensureNow(now);
        if (status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidOrderStateException("Cannot mark payment unknown from status " + status);
        }

        this.status = OrderStatus.PAYMENT_UNKNOWN;
        this.updatedAt = now;
    }

    public void markPaymentCompleted(Instant now) {
        ensureNow(now);
        if (status != OrderStatus.PENDING_PAYMENT && status != OrderStatus.PAYMENT_UNKNOWN) {
            throw new InvalidOrderStateException("Cannot mark payment completed from status " + status);
        }

        this.status = OrderStatus.PENDING_STOCK_CONFIRMATION;
        this.updatedAt = now;
    }

    public void confirm(Instant now) {
        ensureNow(now);
        if (status != OrderStatus.PENDING_STOCK_CONFIRMATION) {
            throw new InvalidOrderStateException("Cannot confirm order from status " + status);
        }

        this.status = OrderStatus.CONFIRMED;
        this.confirmedAt = now;
        this.updatedAt = now;
    }

    public void reject(OrderRejectionReason reason, Instant now) {
        ensureNow(now);

        if (reason == null) {
            throw new IllegalArgumentException("Rejection reason cannot be null");
        }

        ensureRejectionAllowed(reason);

        this.status = OrderStatus.REJECTED;
        this.rejectionReason = reason;
        this.rejectedAt = now;
        this.updatedAt = now;
    }

    private void addSnapshots(List<OrderLineSnapshot> itemSnapshots) {
        Set<UUID> seenProductIds = new HashSet<>();
        for (OrderLineSnapshot snapshot : itemSnapshots) {
            if (snapshot == null) {
                throw new IllegalArgumentException("Order line snapshot cannot be null");
            }

            if (!seenProductIds.add(snapshot.productId())) {
                throw new IllegalArgumentException("Duplicate product in order: " + snapshot.productId());
            }

            this.orderItems.add(OrderItem.create(
                    this,
                    snapshot.productId(),
                    snapshot.productNameSnapshot(),
                    snapshot.productPriceSnapshot(),
                    snapshot.quantity()
            ));
        }
    }

    private void ensureRejectionAllowed(OrderRejectionReason reason) {
        boolean allowed = switch (status) {
            case PENDING_STOCK ->
                    reason == OrderRejectionReason.OUT_OF_STOCK
                            || reason == OrderRejectionReason.USER_CANCELLED;

            case PENDING_PAYMENT ->
                    reason == OrderRejectionReason.PAYMENT_DECLINED
                            || reason == OrderRejectionReason.USER_CANCELLED
                            || reason == OrderRejectionReason.RESERVATION_EXPIRED;

            case PAYMENT_UNKNOWN ->
                    reason == OrderRejectionReason.PAYMENT_DECLINED;

            default -> false;
        };

        if (!allowed) {
            throw new InvalidOrderStateException("Cannot reject order with reason " + reason + " from status " + status);
        }
    }

    private BigDecimal calculateTotalAmount() {
        return orderItems.stream()
                .map(OrderItem::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.UNNECESSARY);
    }

    private void ensureNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Time cannot be null");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getSourceCartId() {
        return sourceCartId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public List<OrderItem> getOrderItems() {
        return List.copyOf(orderItems);
    }

    public OrderStatus getStatus() {
        return status;
    }

    public OrderRejectionReason getRejectionReason() {
        return rejectionReason;
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

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }
}
