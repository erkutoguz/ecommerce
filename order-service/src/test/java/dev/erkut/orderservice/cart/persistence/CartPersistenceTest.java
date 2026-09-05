package dev.erkut.orderservice.cart.persistence;

import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CartPersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID CUSTOMER_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa01");
    private static final UUID CUSTOMER_B = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa02");
    private static final UUID CUSTOMER_C = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03");
    private static final UUID CUSTOMER_D = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa04");
    private static final UUID CUSTOMER_E = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa05");
    private static final UUID PRODUCT_A = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_B = UUID.fromString("90000000-0000-0000-0000-000000000002");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-01-01T10:05:00Z");

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_shouldPersistCartAndItemsAndReloadThemOutsideSession() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID cartId = transactionTemplate.execute(status -> {
            Cart cart = Cart.create(CUSTOMER_A, CREATED_AT);
            cart.addCartItem(PRODUCT_A, 2, CREATED_AT);
            cart.addCartItem(PRODUCT_B, 1, CREATED_AT);
            return cartRepository.save(cart).getId();
        });

        Cart reloaded = transactionTemplate.execute(status ->
                cartRepository.findWithCartItemsById(cartId).orElseThrow()
        );

        assertEquals(CUSTOMER_A, reloaded.getCustomerId());
        assertEquals(CartStatus.ACTIVE, reloaded.getStatus());
        assertEquals(2, reloaded.getCartItems().size());
        assertEquals(PRODUCT_A, reloaded.getCartItems().getFirst().getProductId());
        assertEquals(2, reloaded.getCartItems().getFirst().getQuantity());
        assertEquals(PRODUCT_B, reloaded.getCartItems().get(1).getProductId());
        assertEquals(1, reloaded.getCartItems().get(1).getQuantity());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_duplicateProductInSameCart_shouldViolateUniqueConstraint() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID cartId = transactionTemplate.execute(status -> {
            Cart cart = Cart.create(CUSTOMER_B, CREATED_AT);
            cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
            return cartRepository.save(cart).getId();
        });

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update(
                        """
                                insert into cart_items (id, cart_id, product_id, quantity)
                                values (?, ?, ?, ?)
                                """,
                        UUID.fromString("b0000000-0000-0000-0000-00000000ffff"),
                        cartId,
                        PRODUCT_A,
                        1
                )
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_nonPositiveQuantity_shouldViolateCheckConstraint() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID cartId = transactionTemplate.execute(status ->
                cartRepository.save(Cart.create(CUSTOMER_C, CREATED_AT)).getId()
        );

        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcTemplate.update(
                        """
                                insert into cart_items (id, cart_id, product_id, quantity)
                                values (?, ?, ?, ?)
                                """,
                        UUID.fromString("b0000000-0000-0000-0000-00000000fffe"),
                        cartId,
                        PRODUCT_A,
                        0
                )
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void findByCustomerIdAndStatusIn_shouldReturnActiveAndCheckoutLockedCartsOnly() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID activeId = transactionTemplate.execute(status ->
                cartRepository.save(Cart.create(CUSTOMER_D, CREATED_AT)).getId()
        );
        UUID lockedCustomer = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa06");
        UUID lockedId = transactionTemplate.execute(status -> {
            Cart cart = Cart.create(lockedCustomer, CREATED_AT);
            cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
            cart.lockForCheckout(UPDATED_AT);
            return cartRepository.save(cart).getId();
        });
        UUID completedCustomer = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa07");
        transactionTemplate.executeWithoutResult(status -> {
            Cart cart = Cart.create(completedCustomer, CREATED_AT);
            cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
            cart.lockForCheckout(UPDATED_AT);
            cart.complete(UPDATED_AT.plusSeconds(1));
            cartRepository.save(cart);
        });

        Cart active = transactionTemplate.execute(status ->
                cartRepository.findByCustomerIdAndStatusIn(
                        CUSTOMER_D,
                        List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
                ).orElseThrow()
        );
        Cart locked = transactionTemplate.execute(status ->
                cartRepository.findByCustomerIdAndStatusIn(
                        lockedCustomer,
                        List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
                ).orElseThrow()
        );
        boolean completedFound = Boolean.TRUE.equals(transactionTemplate.execute(status ->
                cartRepository.findByCustomerIdAndStatusIn(
                        completedCustomer,
                        List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
                ).isPresent()
        ));

        assertEquals(activeId, active.getId());
        assertEquals(CartStatus.ACTIVE, active.getStatus());
        assertEquals(lockedId, locked.getId());
        assertEquals(CartStatus.CHECKOUT_LOCKED, locked.getStatus());
        assertEquals(1, locked.getCartItems().size());
        assertFalse(completedFound);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void oneOpenCartPerCustomer_shouldRejectSecondActiveCart() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status ->
                cartRepository.save(Cart.create(CUSTOMER_E, CREATED_AT))
        );

        assertThrows(DataIntegrityViolationException.class, () ->
                transactionTemplate.executeWithoutResult(status ->
                        cartRepository.save(Cart.create(CUSTOMER_E, UPDATED_AT))
                )
        );
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void completedCart_shouldAllowNewActiveCartForSameCustomer() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID customerId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa08");

        transactionTemplate.executeWithoutResult(status -> {
            Cart cart = Cart.create(customerId, CREATED_AT);
            cart.addCartItem(PRODUCT_A, 1, CREATED_AT);
            cart.lockForCheckout(UPDATED_AT);
            cart.complete(UPDATED_AT.plusSeconds(1));
            cartRepository.save(cart);
        });

        UUID newCartId = transactionTemplate.execute(status ->
                cartRepository.save(Cart.create(customerId, UPDATED_AT.plusSeconds(2))).getId()
        );

        Cart openCart = transactionTemplate.execute(status ->
                cartRepository.findByCustomerIdAndStatusIn(
                        customerId,
                        List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
                ).orElseThrow()
        );

        assertEquals(newCartId, openCart.getId());
        assertEquals(CartStatus.ACTIVE, openCart.getStatus());
        assertEquals(2, jdbcTemplate.queryForObject(
                "select count(*) from carts where customer_id = ?",
                Integer.class,
                customerId
        ));
    }
}
