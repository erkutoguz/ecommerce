package dev.erkut.stockservice.reservation.domain;

import dev.erkut.stockservice.reservation.domain.exception.ReservationStatusException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(
            mappedBy = "reservation",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<ReservationItem> items = new ArrayList<>();

    protected Reservation() {}

    private Reservation(UUID orderId, Instant createdAt) {
        this.orderId = orderId;
        this.status = ReservationStatus.RESERVED;
        this.createdAt = createdAt;
    }

    public static Reservation create(UUID orderId, Instant createdAt) {
        return new Reservation(orderId, createdAt);
    }

    public void addItem(UUID productId, int quantity) {
        boolean duplicate = items.stream()
                .anyMatch(item -> item.getProductId().equals(productId));

        if (duplicate) {
            throw new IllegalArgumentException("Product already exists in reservation: " + productId);
        }

        items.add(ReservationItem.create(
                    this,
                    productId,
                    quantity
                )
        );
    }

    public void markConfirmed() {
        ensureReserved();

        this.status = ReservationStatus.CONFIRMED;
    }

    public void releaseStock() {
        ensureReserved();

        this.status = ReservationStatus.RELEASED;
    }

    public void ensureReserved() {
        if (status != ReservationStatus.RESERVED) {
            throw new ReservationStatusException("Reservation must be reserved but was: " + status);
        }
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<ReservationItem> getItems() {
        return items;
    }
}
