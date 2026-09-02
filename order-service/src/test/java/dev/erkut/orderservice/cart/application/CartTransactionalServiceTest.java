package dev.erkut.orderservice.cart.application;

import dev.erkut.orderservice.cart.application.exception.CartNotFoundException;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartTransactionalServiceTest {

    private static final UUID CART_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private CartRepository cartRepository;

    @InjectMocks
    private CartTransactionalService transactionalService;

    @Test
    void create_shouldCreateActiveCartAndSave() {
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);

        Cart result = transactionalService.create(CUSTOMER_ID);

        verify(cartRepository).save(cartCaptor.capture());
        Cart persisted = cartCaptor.getValue();
        assertSame(persisted, result);
        assertEquals(CUSTOMER_ID, persisted.getCustomerId());
        assertEquals(CartStatus.ACTIVE, persisted.getStatus());
        assertEquals(0, persisted.getCartItems().size());
    }

    @Test
    void addCartItem_shouldLoadCartAndApplyDomainMutation() {
        Cart cart = Cart.create(CUSTOMER_ID, NOW);
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.of(cart));

        Cart result = transactionalService.addCartItem(CART_ID, PRODUCT_ID, 2, NOW);

        assertSame(cart, result);
        assertEquals(1, result.getCartItems().size());
        assertEquals(PRODUCT_ID, result.getCartItems().getFirst().getProductId());
        assertEquals(2, result.getCartItems().getFirst().getQuantity());
        verify(cartRepository).findById(CART_ID);
        verify(cartRepository, never()).save(any(Cart.class));
    }

    @Test
    void addCartItem_missingCart_shouldThrowCartNotFoundException() {
        when(cartRepository.findById(CART_ID)).thenReturn(Optional.empty());

        assertThrows(
                CartNotFoundException.class,
                () -> transactionalService.addCartItem(CART_ID, PRODUCT_ID, 1, NOW)
        );
        verify(cartRepository, never()).save(any(Cart.class));
    }
}
