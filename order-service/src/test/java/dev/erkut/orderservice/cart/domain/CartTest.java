package dev.erkut.orderservice.cart.domain;

import dev.erkut.orderservice.cart.domain.exception.CartItemNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CartTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_A = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Test
    void create_validCustomer_shouldStartActiveWithTimestamps() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);

        assertEquals(CUSTOMER_ID, cart.getCustomerId());
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        assertEquals(CREATED_AT, cart.getCreatedAt());
        assertEquals(CREATED_AT, cart.getUpdatedAt());
        assertTrue(cart.getCartItems().isEmpty());
    }

    @Test
    void create_nullCustomerId_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> Cart.create(null, CREATED_AT));
    }

    @Test
    void create_nullNow_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> Cart.create(CUSTOMER_ID, null));
    }

    @Test
    void addCartItem_toActiveCart_shouldAddItemAndUpdateTimestamp() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);

        cart.addCartItem(PRODUCT_A, 2, UPDATED_AT);

        assertEquals(1, cart.getCartItems().size());
        assertEquals(PRODUCT_A, cart.getCartItems().getFirst().getProductId());
        assertEquals(2, cart.getCartItems().getFirst().getQuantity());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
        assertEquals(CREATED_AT, cart.getCreatedAt());
    }

    @Test
    void addCartItem_duplicateProduct_shouldIncreaseExistingQuantity() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);

        cart.addCartItem(PRODUCT_A, 2, UPDATED_AT);

        assertEquals(1, cart.getCartItems().size());
        assertEquals(3, cart.getCartItems().getFirst().getQuantity());
        assertEquals(PRODUCT_A, cart.getCartItems().getFirst().getProductId());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
    }

    @Test
    void addCartItem_nullProductId_shouldThrow() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);

        assertThrows(IllegalArgumentException.class, () -> cart.addCartItem(null, 1, UPDATED_AT));
        assertTrue(cart.getCartItems().isEmpty());
        assertEquals(CREATED_AT, cart.getUpdatedAt());
    }

    @Test
    void addCartItem_nonPositiveQuantity_shouldThrow() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);

        assertThrows(IllegalArgumentException.class, () -> cart.addCartItem(PRODUCT_A, 0, UPDATED_AT));
        assertThrows(IllegalArgumentException.class, () -> cart.addCartItem(PRODUCT_A, -1, UPDATED_AT));
        assertTrue(cart.getCartItems().isEmpty());
    }

    @Test
    void removeCartItem_existingProduct_shouldRemoveAndUpdateTimestamp() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
        cart.addCartItem(PRODUCT_B, 1, CREATED_AT);

        cart.removeCartItem(PRODUCT_A, UPDATED_AT);

        assertEquals(1, cart.getCartItems().size());
        assertEquals(PRODUCT_B, cart.getCartItems().getFirst().getProductId());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
    }

    @Test
    void removeCartItem_missingProduct_shouldThrow() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);

        assertThrows(CartItemNotFoundException.class, () -> cart.removeCartItem(PRODUCT_B, UPDATED_AT));
        assertEquals(1, cart.getCartItems().size());
        assertEquals(CREATED_AT, cart.getUpdatedAt());
    }

    @Test
    void changeCartItemQuantity_increase_shouldIncreaseQuantity() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 2, CREATED_AT);

        cart.changeCartItemQuantity(PRODUCT_A, 3, QuantityChangeType.INCREASE, UPDATED_AT);

        assertEquals(5, cart.getCartItems().getFirst().getQuantity());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
    }

    @Test
    void changeCartItemQuantity_decrease_shouldDecreaseQuantity() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 5, CREATED_AT);

        cart.changeCartItemQuantity(PRODUCT_A, 2, QuantityChangeType.DECREASE, UPDATED_AT);

        assertEquals(3, cart.getCartItems().getFirst().getQuantity());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
    }

    @Test
    void changeCartItemQuantity_decreaseToZeroOrBelow_shouldThrow() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 2, CREATED_AT);

        assertThrows(
                IllegalStateException.class,
                () -> cart.changeCartItemQuantity(PRODUCT_A, 2, QuantityChangeType.DECREASE, UPDATED_AT)
        );
        assertEquals(2, cart.getCartItems().getFirst().getQuantity());
        assertEquals(CREATED_AT, cart.getUpdatedAt());
    }

    @Test
    void changeCartItemQuantity_invalidQuantity_shouldThrow() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 2, CREATED_AT);

        assertThrows(
                IllegalArgumentException.class,
                () -> cart.changeCartItemQuantity(PRODUCT_A, 0, QuantityChangeType.INCREASE, UPDATED_AT)
        );
        assertEquals(2, cart.getCartItems().getFirst().getQuantity());
    }

    @Test
    void changeCartItemQuantity_missingProduct_shouldThrow() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);

        assertThrows(
                CartItemNotFoundException.class,
                () -> cart.changeCartItemQuantity(PRODUCT_B, 1, QuantityChangeType.INCREASE, UPDATED_AT)
        );
    }

    @Test
    void lockForCheckout_fromActiveWithItems_shouldLock() {
        Cart cart = activeCartWithItem();

        cart.lockForCheckout(UPDATED_AT);

        assertEquals(CartStatus.CHECKOUT_LOCKED, cart.getStatus());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
    }

    @Test
    void lockForCheckout_emptyCart_shouldThrow() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);

        assertThrows(IllegalStateException.class, () -> cart.lockForCheckout(UPDATED_AT));
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        assertEquals(CREATED_AT, cart.getUpdatedAt());
    }

    @Test
    void reopen_fromCheckoutLocked_shouldBecomeActive() {
        Cart cart = checkoutLockedCart();

        cart.reopen(UPDATED_AT);

        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
    }

    @Test
    void complete_fromCheckoutLocked_shouldBecomeCompleted() {
        Cart cart = checkoutLockedCart();

        cart.complete(UPDATED_AT);

        assertEquals(CartStatus.COMPLETED, cart.getStatus());
        assertEquals(UPDATED_AT, cart.getUpdatedAt());
    }

    @Test
    void reopen_fromCompleted_shouldThrow() {
        Cart cart = completedCart();

        assertThrows(IllegalStateException.class, () -> cart.reopen(UPDATED_AT));
        assertEquals(CartStatus.COMPLETED, cart.getStatus());
    }

    @Test
    void complete_fromActive_shouldThrow() {
        Cart cart = activeCartWithItem();

        assertThrows(IllegalStateException.class, () -> cart.complete(UPDATED_AT));
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
    }

    @Test
    void reopen_fromActive_shouldThrow() {
        Cart cart = activeCartWithItem();

        assertThrows(IllegalStateException.class, () -> cart.reopen(UPDATED_AT));
        assertEquals(CartStatus.ACTIVE, cart.getStatus());
    }

    @Test
    void addCartItem_whenCheckoutLocked_shouldThrow() {
        Cart cart = checkoutLockedCart();

        assertThrows(IllegalStateException.class, () -> cart.addCartItem(PRODUCT_B, 1, UPDATED_AT));
        assertEquals(1, cart.getCartItems().size());
        assertEquals(CartStatus.CHECKOUT_LOCKED, cart.getStatus());
    }

    @Test
    void removeCartItem_whenCheckoutLocked_shouldThrow() {
        Cart cart = checkoutLockedCart();

        assertThrows(IllegalStateException.class, () -> cart.removeCartItem(PRODUCT_A, UPDATED_AT));
        assertEquals(1, cart.getCartItems().size());
    }

    @Test
    void changeCartItemQuantity_whenCheckoutLocked_shouldThrow() {
        Cart cart = checkoutLockedCart();

        assertThrows(
                IllegalStateException.class,
                () -> cart.changeCartItemQuantity(PRODUCT_A, 1, QuantityChangeType.INCREASE, UPDATED_AT)
        );
        assertEquals(1, cart.getCartItems().getFirst().getQuantity());
    }

    @Test
    void addCartItem_whenCompleted_shouldThrow() {
        Cart cart = completedCart();

        assertThrows(IllegalStateException.class, () -> cart.addCartItem(PRODUCT_B, 1, UPDATED_AT));
        assertEquals(CartStatus.COMPLETED, cart.getStatus());
    }

    @Test
    void removeCartItem_whenCompleted_shouldThrow() {
        Cart cart = completedCart();

        assertThrows(IllegalStateException.class, () -> cart.removeCartItem(PRODUCT_A, UPDATED_AT));
        assertEquals(1, cart.getCartItems().size());
    }

    @Test
    void changeCartItemQuantity_whenCompleted_shouldThrow() {
        Cart cart = completedCart();

        assertThrows(
                IllegalStateException.class,
                () -> cart.changeCartItemQuantity(PRODUCT_A, 1, QuantityChangeType.DECREASE, UPDATED_AT)
        );
        assertEquals(1, cart.getCartItems().getFirst().getQuantity());
    }

    @Test
    void getCartItems_shouldBeUnmodifiable() {
        Cart cart = activeCartWithItem();

        assertThrows(UnsupportedOperationException.class, () -> cart.getCartItems().clear());
        assertEquals(1, cart.getCartItems().size());
    }

    private static Cart activeCartWithItem() {
        Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
        cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
        return cart;
    }

    private static Cart checkoutLockedCart() {
        Cart cart = activeCartWithItem();
        cart.lockForCheckout(CREATED_AT.plusSeconds(1));
        return cart;
    }

    private static Cart completedCart() {
        Cart cart = checkoutLockedCart();
        cart.complete(CREATED_AT.plusSeconds(2));
        return cart;
    }
}
