package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.message.command.ConfirmOrderCommand;
import dev.erkut.orderservice.message.event.OrderConfirmedEvent;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderStatus;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import dev.erkut.orderservice.outbox.application.OutboxService;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class OrderConfirmationRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa71");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000071");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final RuntimeException OUTBOX_FAILURE = new RuntimeException("outbox failure");

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxService outboxService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM outbox_messages");
        jdbcTemplate.update("DELETE FROM order_items");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM cart_items");
        jdbcTemplate.update("DELETE FROM carts");
    }

    @Test
    void outboxFailure_shouldRollbackOrderCartAndInbox() {
        Seed seed = seedFinalOrder();
        ConfirmOrderCommand command = new ConfirmOrderCommand(seed.orderId());
        MessageEnvelope envelope = new MessageEnvelope(
                UUID.randomUUID(),
                "CONFIRM_ORDER_COMMAND",
                CREATED_AT.plusSeconds(10),
                new tools.jackson.databind.json.JsonMapper().valueToTree(command)
        );
        doThrow(OUTBOX_FAILURE)
                .when(outboxService)
                .createOrderConfirmedEvent(any(OrderConfirmedEvent.class), any(Instant.class));

        assertEquals(
                OUTBOX_FAILURE,
                assertThrows(
                        RuntimeException.class,
                        () -> orderService.handleConfirmOrderCommand(envelope, command)
                )
        );

        assertEquals(OrderStatus.PENDING_STOCK_CONFIRMATION, loadOrder(seed.orderId()).getStatus());
        assertEquals(CartStatus.CHECKOUT_LOCKED, loadCart(seed.cartId()).getStatus());
        assertFalse(inboxRepository.existsById(envelope.messageId()));
        assertTrue(outboxRepository.findAll().isEmpty());
    }

    private Seed seedFinalOrder() {
        return transaction().execute(status -> {
            Cart cart = Cart.create(CUSTOMER_ID, CREATED_AT);
            cart.addCartItem(PRODUCT_ID, 1, CREATED_AT);
            cart.lockForCheckout(CREATED_AT.plusSeconds(1));
            cartRepository.save(cart);

            Order order = Order.create(
                    cart.getId(),
                    CUSTOMER_ID,
                    Currency.TRY,
                    List.of(new OrderLineSnapshot(PRODUCT_ID, "Test product", new BigDecimal("100.00"), 1)),
                    CREATED_AT
            );
            order.markStockReserved(CREATED_AT.plusSeconds(2));
            order.markPaymentCompleted(CREATED_AT.plusSeconds(3));
            orderRepository.save(order);
            return new Seed(order.getId(), cart.getId());
        });
    }

    private Order loadOrder(UUID orderId) {
        return transaction().execute(status -> orderRepository.findById(orderId).orElseThrow());
    }

    private Cart loadCart(UUID cartId) {
        return transaction().execute(status -> cartRepository.findById(cartId).orElseThrow());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record Seed(UUID orderId, UUID cartId) {}
}
