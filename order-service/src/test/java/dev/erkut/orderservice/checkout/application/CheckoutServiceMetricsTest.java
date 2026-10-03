package dev.erkut.orderservice.checkout.application;

import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.integration.product.ProductClient;
import dev.erkut.orderservice.integration.product.ProductLookupResponse;
import dev.erkut.orderservice.integration.product.ProductStatus;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.observability.metric.OrderMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceMetricsTest {

    private static final UUID CART_ID = UUID.fromString("80000000-0000-0000-0000-000000000021");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa21");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000021");
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private CartService cartService;

    @Mock
    private ProductClient productClient;

    @Mock
    private CheckoutTransactionalService transactionalService;

    private SimpleMeterRegistry registry;
    private CheckoutService checkoutService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        checkoutService = new CheckoutService(
                cartService,
                productClient,
                transactionalService,
                new OrderMetrics(registry)
        );
    }

    @Test
    void checkout_success_shouldCountAttemptAndCompletionAfterTransactionalOperation() {
        Cart cart = Cart.create(CUSTOMER_ID, NOW);
        cart.addCartItem(PRODUCT_ID, 1, NOW);
        when(cartService.getCartById(CART_ID, CUSTOMER_ID)).thenReturn(cart);
        when(productClient.getProductsByIds(any())).thenReturn(List.of(
                new ProductLookupResponse(PRODUCT_ID, "Product", new BigDecimal("10.00"), ProductStatus.ACTIVE)
        ));
        Order order = org.mockito.Mockito.mock(Order.class);
        when(transactionalService.checkout(
                isNull(),
                eq(CUSTOMER_ID),
                eq(0L),
                eq(Currency.TRY),
                anyList(),
                any(Instant.class)
        )).thenReturn(order);

        assertSame(order, checkoutService.checkout(CART_ID, CUSTOMER_ID, Currency.TRY));

        assertEquals(1.0, registry.get("checkout.attempts").counter().count());
        assertEquals(1.0, registry.get("checkout.accepted").counter().count());
        assertEquals(0.0, registry.get("checkout.failed").counter().count());
        verify(transactionalService).checkout(
                isNull(), eq(CUSTOMER_ID), eq(0L), eq(Currency.TRY), anyList(), any(Instant.class)
        );
    }

    @Test
    void checkout_productLookupFailure_shouldCountFailureAndRethrow() {
        Cart cart = Cart.create(CUSTOMER_ID, NOW);
        cart.addCartItem(PRODUCT_ID, 1, NOW);
        when(cartService.getCartById(CART_ID, CUSTOMER_ID)).thenReturn(cart);
        RuntimeException failure = new IllegalStateException("product service unavailable");
        when(productClient.getProductsByIds(any())).thenThrow(failure);

        assertSame(failure, assertThrows(
                RuntimeException.class,
                () -> checkoutService.checkout(CART_ID, CUSTOMER_ID, Currency.TRY)
        ));

        assertEquals(1.0, registry.get("checkout.attempts").counter().count());
        assertEquals(0.0, registry.get("checkout.accepted").counter().count());
        assertEquals(1.0, registry.get("checkout.failed").counter().count());
    }
}
