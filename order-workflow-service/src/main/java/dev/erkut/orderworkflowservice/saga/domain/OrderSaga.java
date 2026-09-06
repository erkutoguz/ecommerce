package dev.erkut.orderworkflowservice.saga.domain;

import jakarta.persistence.*;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 50)
    private OrderSagaState state;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderSaga () {}

    private OrderSaga(UUID orderId, UUID customerId, Instant now) {
        if(orderId == null) {
            throw new IllegalArgumentException("Order id cannot be null");
        }

        if(customerId == null) {
            throw new IllegalArgumentException("Customer id cannot be null");
        }

        if(now == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.orderId = orderId;
        this.customerId = customerId;
        this.state = OrderSagaState.STOCK_RESERVATION_PENDING;
        this.updatedAt = now;
        this.createdAt = now;
    }

    public static OrderSaga start(UUID orderId, UUID customerId, Instant now) {
        return new OrderSaga(orderId, customerId, now);
    }

    public UUID getOrderId() {
        return orderId;
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
