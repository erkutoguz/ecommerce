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
import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class RejectOrderCommandIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

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

    private final JsonMapper jsonMapper = new JsonMapper();

    @Test
    void rejectCommand_shouldAtomicallyPersistInboxRejectedOrderAndSingleOutboxAcrossDuplicateDelivery()
            throws Exception {
        UUID orderId = seedPendingOrder();
        UUID messageId = UUID.randomUUID();
        RejectOrderCommand command = command(orderId);
        MessageEnvelope envelope = envelope(messageId, command);

        orderService.handleRejectOrderCommand(envelope, command);

        Order rejected = loadOrder(orderId);
        List<OutboxMessage> afterFirstDelivery = rejectedEvents(orderId);
        assertEquals(OrderStatus.REJECTED, rejected.getStatus());
        assertEquals(OrderRejectionReason.OUT_OF_STOCK, rejected.getRejectionReason());
        assertNotNull(rejected.getRejectedAt());
        assertNotNull(rejected.getUpdatedAt());
        assertEquals(rejected.getRejectedAt(), rejected.getUpdatedAt());
        assertEquals(CartStatus.ACTIVE, loadCart(rejected.getSourceCartId()).getStatus());
        assertEquals(1, inboxRepository.findAll().stream()
                .filter(message -> message.getMessageId().equals(messageId))
                .count());
        assertEquals(1, afterFirstDelivery.size());

        OutboxMessage outbox = afterFirstDelivery.getFirst();
        assertEquals(OutboxMessageType.ORDER_REJECTED_EVENT, outbox.getMessageType());
        assertEquals(orderId, outbox.getAggregateId());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(orderId, jsonMapper.treeToValue(outbox.getPayload(), OrderRejectedEvent.class).orderId());

        Instant rejectedAt = rejected.getRejectedAt();
        orderService.handleRejectOrderCommand(envelope, command);

        assertEquals(1, rejectedEvents(orderId).size());
        assertEquals(rejectedAt, loadOrder(orderId).getRejectedAt());
        assertEquals(1, inboxRepository.findAll().stream()
                .filter(message -> message.getMessageId().equals(messageId))
                .count());
    }

    @Test
    void orderNotFound_shouldRollbackInboxAndCreateNoOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        RejectOrderCommand command = command(orderId);

        assertThrows(
                OrderNotFoundException.class,
                () -> orderService.handleRejectOrderCommand(envelope(messageId, command), command)
        );

        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, rejectedEvents(orderId).size());
    }

    @Test
    void invalidOrderState_shouldRollbackInboxAndCreateNoNewOutbox() {
        UUID orderId = seedRejectedOrder();
        UUID messageId = UUID.randomUUID();
        RejectOrderCommand command = command(orderId);

        assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.handleRejectOrderCommand(envelope(messageId, command), command)
        );

        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, rejectedEvents(orderId).size());
        assertEquals(OrderStatus.REJECTED, loadOrder(orderId).getStatus());
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
                            "Reject flow product",
                            new BigDecimal("100.00"),
                            1
                    )),
                    CREATED_AT
            );
            return orderRepository.save(order).getId();
        });
    }

    private UUID seedRejectedOrder() {
        UUID orderId = seedPendingOrder();
        transaction().executeWithoutResult(status ->
                orderRepository.findById(orderId).orElseThrow()
                        .reject(OrderRejectionReason.OUT_OF_STOCK, CREATED_AT.plusSeconds(30))
        );
        return orderId;
    }

    private Order loadOrder(UUID orderId) {
        return transaction().execute(status -> orderRepository.findById(orderId).orElseThrow());
    }

    private Cart loadCart(UUID cartId) {
        return transaction().execute(status -> cartRepository.findById(cartId).orElseThrow());
    }

    private List<OutboxMessage> rejectedEvents(UUID orderId) {
        return transaction().execute(status -> outboxRepository.findAll().stream()
                .filter(message -> message.getAggregateId().equals(orderId))
                .filter(message -> message.getMessageType() == OutboxMessageType.ORDER_REJECTED_EVENT)
                .toList());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private RejectOrderCommand command(UUID orderId) {
        return new RejectOrderCommand(orderId, OrderRejectionReason.OUT_OF_STOCK);
    }

    private MessageEnvelope envelope(UUID messageId, RejectOrderCommand command) {
        return new MessageEnvelope(
                messageId,
                "REJECT_ORDER_COMMAND",
                CREATED_AT.plusSeconds(60),
                jsonMapper.valueToTree(command)
        );
    }
}
