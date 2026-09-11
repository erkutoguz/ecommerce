package dev.erkut.stockservice.reservation.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "reservation_items")
public class ReservationItem {

    @EmbeddedId
    private ReservationItemId id;

    @MapsId("orderId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Reservation reservation;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected ReservationItem() {}

    private ReservationItem(
            Reservation reservation,
            UUID productId,
            int quantity
    ) {
        if (reservation == null) {
            throw new IllegalArgumentException("Reservation cannot be null");
        }

        if (productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }

        this.reservation = reservation;
        this.id = new ReservationItemId(
                reservation.getOrderId(),
                productId
        );
        this.quantity = quantity;
    }

    static ReservationItem create(
            Reservation reservation,
            UUID productId,
            int quantity
    ) {
        return new ReservationItem(reservation, productId, quantity);
    }

    public UUID getProductId() {
        return id.getProductId();
    }

    public int getQuantity() {
        return quantity;
    }
}