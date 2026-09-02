package dev.erkut.orderservice.cart.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(
        name = "cart_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_items_cart_product",
                columnNames = {"cart_id", "product_id"}
        )
)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    protected CartItem() {}

    private CartItem(Cart cart, UUID productId, int quantity) {
        if(cart == null) {
            throw new IllegalArgumentException("Cart cannot be null");
        }

        if(productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if(quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        this.cart = cart;
        this.productId = productId;
        this.quantity = quantity;
    }

    static CartItem create(Cart cart, UUID productId, int quantity) {
        return new CartItem(cart, productId, quantity);
    }

    void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        this.quantity = quantity;
    }

    boolean hasProduct(UUID productId) {
        return this.productId.equals(productId);
    }

    public UUID getId() {
        return id;
    }

    public UUID getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }
}
