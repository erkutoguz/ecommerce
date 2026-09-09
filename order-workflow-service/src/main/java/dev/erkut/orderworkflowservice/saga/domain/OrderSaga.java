package dev.erkut.orderworkflowservice.saga.domain;

import dev.erkut.orderworkflowservice.saga.domain.exception.IllegalOrderSagaStateException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_sagas")
public class OrderSaga {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(
            name = "total_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 3)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private OrderSagaState state;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderSaga () {}

    private OrderSaga(
            UUID orderId,
            BigDecimal totalAmount,
            Currency currency,
            UUID customerId,
            Instant now) {
        if(orderId == null) {
            throw new IllegalArgumentException("Order id cannot be null");
        }

        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero");
        }

        if(currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        if(customerId == null) {
            throw new IllegalArgumentException("Customer id cannot be null");
        }

        if(now == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.customerId = customerId;
        this.state = OrderSagaState.STOCK_RESERVATION_PENDING;
        this.updatedAt = now;
        this.createdAt = now;
    }

    public static OrderSaga start(
            UUID orderId,
            BigDecimal totalAmount,
            Currency currency,
            UUID customerId,
            Instant now
    ) {
        return new OrderSaga(orderId, totalAmount, currency, customerId, now);
    }

    public void markOrderRejectionPending(Instant updatedAt) {
        if (state != OrderSagaState.STOCK_RESERVATION_PENDING) {
            throw new IllegalOrderSagaStateException("Order rejection cannot be started from state: " + state);
        }

        state = OrderSagaState.ORDER_REJECTION_PENDING;
        this.updatedAt = updatedAt;
    }

    public void markPaymentPending(Instant updatedAt) {
        if(state != OrderSagaState.STOCK_RESERVATION_PENDING) {
            throw new IllegalOrderSagaStateException("Payment pending cannot be started from state: " + state);
        }

        state = OrderSagaState.PAYMENT_PENDING;
        this.updatedAt = updatedAt;
    }

    public void markFailed(Instant updatedAt) {
        if (state != OrderSagaState.ORDER_REJECTION_PENDING) {
            throw new IllegalOrderSagaStateException("Order saga cannot be marked as failed from state: " + state);
        }

        state = OrderSagaState.FAILED;
        this.updatedAt = updatedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public OrderSagaState getState() {
        return state;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
