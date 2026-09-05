package dev.erkut.orderservice.checkout.application;

import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import dev.erkut.orderservice.outbox.application.OutboxService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class CheckoutTransactionalRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa17");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000017");
    private static final Instant CHECKOUT_AT = Instant.parse("2026-01-01T10:05:00Z");
    private static final RuntimeException OUTBOX_FAILURE = new RuntimeException("outbox failure");

    @Autowired
    private CheckoutTransactionalService checkoutTransactionalService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private OutboxService outboxService;

    @Test
    void checkout_shouldRollbackCartAndOrderWhenOutboxCreationFails() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        UUID cartId = seedCart(transactionTemplate);
        doThrow(OUTBOX_FAILURE)
                .when(outboxService)
                .createOrderCheckoutStartedMessage(any(OrderCheckoutStartedEvent.class), any(Instant.class));

        assertThrows(
                RuntimeException.class,
                () -> checkoutTransactionalService.checkout(
                        cartId,
                        0,
                        Currency.TRY,
                        List.of(new OrderLineSnapshot(PRODUCT_ID, "Product 17", new BigDecimal("100.00"), 1)),
                        CHECKOUT_AT
                )
        );

        Cart cart = transactionTemplate.execute(status -> cartRepository.findById(cartId).orElseThrow());
        long ordersForCart = transactionTemplate.execute(status ->
                orderRepository.findAll().stream()
                        .filter(order -> order.getSourceCartId().equals(cartId))
                        .count()
        );

        assertEquals(CartStatus.ACTIVE, cart.getStatus());
        assertEquals(0, ordersForCart);
    }

    private UUID seedCart(TransactionTemplate transactionTemplate) {
        return transactionTemplate.execute(status -> {
            Cart cart = Cart.create(CUSTOMER_ID, CHECKOUT_AT.minusSeconds(60));
            cart.addCartItem(PRODUCT_ID, 1, CHECKOUT_AT.minusSeconds(30));
            return cartRepository.save(cart).getId();
        });
    }
}
