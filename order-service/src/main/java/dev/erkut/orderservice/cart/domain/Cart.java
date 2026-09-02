package dev.erkut.orderservice.cart.domain;

import dev.erkut.orderservice.cart.domain.exception.CartItemNotFoundException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CartStatus status;

    @OneToMany(
            mappedBy = "cart",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<CartItem> cartItems = new ArrayList<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Cart() {}

    private Cart(UUID customerId, Instant now) {
        if(customerId == null) {
            throw new IllegalArgumentException("Customer id cannot be null");
        }

        if (now == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        this.customerId = customerId;
        this.status = CartStatus.ACTIVE;
        this.updatedAt = now;
        this.createdAt = now;
    }

    public static Cart create(UUID customerId, Instant now) {
        return new Cart(customerId, now);
    }

    public void addCartItem(UUID productId, int quantity, Instant now) {
        ensureActive();

        validateArguments(productId, quantity, now);

        cartItems.stream()
                .filter(cartItem -> cartItem.hasProduct(productId))
                .findFirst()
                .ifPresentOrElse(
                        cartItem -> cartItem.changeQuantity(cartItem.getQuantity() + quantity),
                        () -> cartItems.add(
                                CartItem.create(this, productId, quantity)
                        )
                );

        updateTime(now);
    }

    public void removeCartItem(UUID productId, Instant now) {
        ensureActive();
        ensureNow(now);

        if(productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        CartItem cartItem = findCartItem(productId);
        cartItems.remove(cartItem);

        updateTime(now);
    }

    public void changeCartItemQuantity(UUID productId, int quantity, Instant now) {
        ensureActive();

        validateArguments(productId, quantity, now);

        CartItem cartItem = findCartItem(productId);

        cartItem.changeQuantity(quantity);

        updateTime(now);
    }

    public void lockForCheckout(Instant now) {
        ensureNow(now);
        ensureActive();

        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Empty cart cannot be checked out");
        }

        this.status = CartStatus.CHECKOUT_LOCKED;
        updateTime(now);
    }

    public void reopen(Instant now) {
        ensureNow(now);
        ensureCheckoutLocked();

        this.status = CartStatus.ACTIVE;
        updateTime(now);
    }

    public void complete(Instant now) {
        ensureNow(now);
        ensureCheckoutLocked();

        this.status = CartStatus.COMPLETED;
        updateTime(now);
    }

    private void validateArguments(UUID productId, int quantity, Instant now) {
        if(productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if(quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        if(now == null) {
            throw new IllegalArgumentException("Time cannot be null");
        }
    }

    private void ensureActive() {
        if (status != CartStatus.ACTIVE) {
            throw new IllegalStateException("Cart must be active");
        }
    }

    private void ensureCheckoutLocked() {
        if (status != CartStatus.CHECKOUT_LOCKED) {
            throw new IllegalStateException("Cart must be locked for checkout");
        }
    }

    private void ensureNow(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("Time cannot be null");
        }
    }

    private CartItem findCartItem(UUID productId) {
        return cartItems.stream()
                .filter(cartItem -> cartItem.hasProduct(productId))
                .findFirst()
                .orElseThrow(() -> new CartItemNotFoundException("Cart item not found with product id: " + productId));
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

    public CartStatus getStatus() {
        return status;
    }

    public List<CartItem> getCartItems() {
        return List.copyOf(cartItems);
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
