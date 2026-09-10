package dev.erkut.paymentservice.payment.domain;

import dev.erkut.paymentservice.payment.domain.exception.InvalidPaymentStateException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

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
    @Column(name = "status", nullable = false, length = 40)
    private PaymentStatus status;

    @Column(name = "provider_payment_id", nullable = false, unique = true)
    private String providerPaymentId;

    @Column(name = "checkout_url", nullable = false)
    private String checkoutUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected Payment() {}

    private Payment(UUID orderId, BigDecimal totalAmount, Currency currency, Instant createdAt) {
        if(orderId == null) {
            throw new IllegalArgumentException("Order id cannot be null");
        }

        if (totalAmount == null || totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Total amount must be greater than zero");
        }

        if(currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        if(createdAt == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.orderId = orderId;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.status = PaymentStatus.PROCESSING;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.processedAt = null;
    }

    public static Payment create(UUID orderId, BigDecimal totalAmount, Currency currency, Instant createdAt) {
        return new Payment(orderId, totalAmount, currency, createdAt);
    }

    public void markAwaitingCustomerAction(
            String providerPaymentId,
            String checkoutUrl,
            Instant updatedAt
    ) {
        if (status != PaymentStatus.PROCESSING) {
            throw new InvalidPaymentStateException("Payment cannot await customer action from state: " + status);
        }

        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment id cannot be blank");
        }

        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new IllegalArgumentException("Checkout URL cannot be blank");
        }

        if (updatedAt == null) {
            throw new IllegalArgumentException("Update time cannot be null");
        }

        this.providerPaymentId = providerPaymentId;
        this.checkoutUrl = checkoutUrl;
        this.updatedAt = updatedAt;
        this.status = PaymentStatus.AWAITING_CUSTOMER_ACTION;
    }

    public void markCompleted(Instant occurredAt, Instant updatedAt) {
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurrence time cannot be null");
        }

        if (updatedAt == null) {
            throw new IllegalArgumentException("Update time cannot be null");
        }

        if (status != PaymentStatus.AWAITING_CUSTOMER_ACTION) {
            throw new InvalidPaymentStateException("Payment cannot be completed from state: " + status);
        }

        this.status = PaymentStatus.COMPLETED;
        this.processedAt = occurredAt;
        this.updatedAt = updatedAt;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public Long getVersion() {
        return version;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Currency getCurrency() {
        return currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
