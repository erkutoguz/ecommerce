package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.message.command.RejectOrderCommand;
import dev.erkut.orderservice.message.event.OrderRejectedEvent;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import dev.erkut.orderservice.order.domain.OrderStatus;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import dev.erkut.orderservice.outbox.application.OutboxService;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
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
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class RejectOrderCommandRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

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

    @MockitoBean
    private OutboxService outboxService;

    @Test
    void outboxFailure_shouldRollbackOrderMutationAndInboxClaim() {
        UUID orderId = seedPendingOrder();
        UUID messageId = UUID.randomUUID();
        RejectOrderCommand command = new RejectOrderCommand(orderId, OrderRejectionReason.OUT_OF_STOCK);
        MessageEnvelope envelope = new MessageEnvelope(
                messageId,
                "REJECT_ORDER_COMMAND",
                CREATED_AT.plusSeconds(60),
                new JsonMapper().valueToTree(command)
        );
        doThrow(OUTBOX_FAILURE)
                .when(outboxService)
                .createOrderRejectedEvent(any(OrderRejectedEvent.class), any(Instant.class));

        RuntimeException thrown = assertThrows(
                RuntimeException.class,
                () -> orderService.handleRejectOrderCommand(envelope, command)
        );

        assertEquals(OUTBOX_FAILURE, thrown);
        Order reloaded = transaction().execute(status -> orderRepository.findById(orderId).orElseThrow());
        assertEquals(OrderStatus.PENDING_STOCK, reloaded.getStatus());
        assertNull(reloaded.getRejectionReason());
        assertNull(reloaded.getRejectedAt());
        assertEquals(CartStatus.CHECKOUT_LOCKED,
                transaction().execute(status -> cartRepository.findById(reloaded.getSourceCartId())
                        .orElseThrow().getStatus()));
        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, outboxRepository.findAll().stream()
                .filter(message -> message.getAggregateId().equals(orderId))
                .count());
    }

    private UUID seedPendingOrder() {
        return transaction().execute(status -> {
            UUID customerId = UUID.randomUUID();
            UUID productId = UUID.randomUUID();
            Cart cart = Cart.create(customerId, CREATED_AT.minusSeconds(60));
            cart.addCartItem(productId, 1, CREATED_AT.minusSeconds(50));
            cart.lockForCheckout(CREATED_AT.minusSeconds(40));
            cartRepository.save(cart);
            Order order = Order.create(
                    cart.getId(),
                    customerId,
                    Currency.TRY,
                    List.of(new OrderLineSnapshot(
                            productId,
                            "Rollback product",
                            new BigDecimal("100.00"),
                            1
                    )),
                    CREATED_AT
            );
            return orderRepository.save(order).getId();
        });
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
}
