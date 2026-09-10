package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.message.command.ConfirmOrderCommand;
import dev.erkut.orderservice.message.command.MarkOrderPaymentCompletedCommand;
import dev.erkut.orderservice.message.command.MarkOrderStockReservedCommand;
import dev.erkut.orderservice.message.event.OrderConfirmedEvent;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderStatus;
import dev.erkut.orderservice.order.domain.exception.InvalidOrderStateException;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class OrderCommandFlowIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa70");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000070");
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JsonMapper jsonMapper;

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
    void markStockReserved_shouldTransitionOnceAndCreateNoOutboxEvent() {
        UUID orderId = seedOrder(OrderStatus.PENDING_STOCK, false);
        UUID messageId = UUID.randomUUID();
        MarkOrderStockReservedCommand command = new MarkOrderStockReservedCommand(orderId);
        MessageEnvelope envelope = envelope(messageId, "MARK_ORDER_STOCK_RESERVED_COMMAND", command);

        orderService.handleMarkOrderStockReservedCommand(envelope, command);
        orderService.handleMarkOrderStockReservedCommand(envelope, command);

        assertEquals(OrderStatus.PENDING_PAYMENT, loadOrder(orderId).getStatus());
        assertEquals(1, inboxRepository.count());
        assertTrue(outboxRepository.findAll().isEmpty());
    }

    @Test
    void markPaymentCompleted_shouldTransitionOnceAndCreateNoOutboxEvent() {
        UUID orderId = seedOrder(OrderStatus.PENDING_PAYMENT, false);
        UUID messageId = UUID.randomUUID();
        MarkOrderPaymentCompletedCommand command = new MarkOrderPaymentCompletedCommand(orderId);
        MessageEnvelope envelope = envelope(messageId, "MARK_ORDER_PAYMENT_COMPLETED_COMMAND", command);

        orderService.handleMarkOrderPaymentCompletedCommand(envelope, command);
        orderService.handleMarkOrderPaymentCompletedCommand(envelope, command);

        assertEquals(OrderStatus.PENDING_STOCK_CONFIRMATION, loadOrder(orderId).getStatus());
        assertEquals(1, inboxRepository.count());
        assertTrue(outboxRepository.findAll().isEmpty());
    }

    @Test
    void intermediateCommands_shouldRejectInvalidSourceStatesAndRollbackInboxClaim() {
        UUID stockReservedOrderId = seedOrder(OrderStatus.PENDING_PAYMENT, false);
        MarkOrderStockReservedCommand stockCommand = new MarkOrderStockReservedCommand(stockReservedOrderId);
        MessageEnvelope stockEnvelope = envelope(
                UUID.randomUUID(),
                "MARK_ORDER_STOCK_RESERVED_COMMAND",
                stockCommand
        );

        assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.handleMarkOrderStockReservedCommand(stockEnvelope, stockCommand)
        );

        UUID paymentCompletedOrderId = seedOrder(OrderStatus.PENDING_STOCK, false);
        MarkOrderPaymentCompletedCommand paymentCommand =
                new MarkOrderPaymentCompletedCommand(paymentCompletedOrderId);
        MessageEnvelope paymentEnvelope = envelope(
                UUID.randomUUID(),
                "MARK_ORDER_PAYMENT_COMPLETED_COMMAND",
                paymentCommand
        );

        assertThrows(
                InvalidOrderStateException.class,
                () -> orderService.handleMarkOrderPaymentCompletedCommand(paymentEnvelope, paymentCommand)
        );

        assertEquals(OrderStatus.PENDING_PAYMENT, loadOrder(stockReservedOrderId).getStatus());
        assertEquals(OrderStatus.PENDING_STOCK, loadOrder(paymentCompletedOrderId).getStatus());
        assertEquals(0, inboxRepository.count());
        assertTrue(outboxRepository.findAll().isEmpty());
    }

    @Test
    void orderConfirmation_shouldCompleteOrderCartAndCreateOneMinimalEvent() throws Exception {
        Seed seed = seedOrderWithCart(OrderStatus.PENDING_STOCK_CONFIRMATION, true);
        ConfirmOrderCommand command = new ConfirmOrderCommand(seed.orderId());
        MessageEnvelope envelope = envelope(UUID.randomUUID(), "CONFIRM_ORDER_COMMAND", command);

        orderService.handleConfirmOrderCommand(envelope, command);

        assertEquals(OrderStatus.CONFIRMED, loadOrder(seed.orderId()).getStatus());
        assertEquals(CartStatus.COMPLETED, loadCart(seed.cartId()).getStatus());

        List<OutboxMessage> events = outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType() == OutboxMessageType.ORDER_CONFIRMED_EVENT)
                .toList();
        assertEquals(1, events.size());
        OutboxMessage event = events.getFirst();
        assertEquals(seed.orderId(), event.getAggregateId());
        assertEquals(1, event.getPayload().size());
        assertEquals(seed.orderId().toString(), event.getPayload().get("orderId").asString());
        assertEquals(seed.orderId(), jsonMapper.treeToValue(event.getPayload(), OrderConfirmedEvent.class).orderId());
        assertEquals(1, inboxRepository.count());
    }

    @Test
    void orderVersion_shouldRejectConcurrentStaleUpdate() throws Exception {
        UUID orderId = seedOrder(OrderStatus.PENDING_STOCK, false);
        CountDownLatch bothTransactionsLoaded = new CountDownLatch(2);
        CountDownLatch releaseUpdates = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Throwable> first = executor.submit(() -> updateOrder(orderId, bothTransactionsLoaded, releaseUpdates));
            Future<Throwable> second = executor.submit(() -> updateOrder(orderId, bothTransactionsLoaded, releaseUpdates));

            assertTrue(bothTransactionsLoaded.await(10, TimeUnit.SECONDS));
            releaseUpdates.countDown();

            Throwable firstFailure = first.get(20, TimeUnit.SECONDS);
            Throwable secondFailure = second.get(20, TimeUnit.SECONDS);
            assertEquals(1, (firstFailure == null ? 1 : 0) + (secondFailure == null ? 1 : 0));
            Throwable optimisticLockFailure = firstFailure != null ? firstFailure : secondFailure;
            assertTrue(optimisticLockFailure != null);
            assertTrue(isOptimisticLockFailure(optimisticLockFailure));
            assertEquals(OrderStatus.PENDING_PAYMENT, loadOrder(orderId).getStatus());
            assertEquals(1L, jdbcTemplate.queryForObject(
                    "SELECT version FROM orders WHERE id = ?",
                    Long.class,
                    orderId
            ));
        } finally {
            releaseUpdates.countDown();
            executor.shutdownNow();
        }
    }

    private Throwable updateOrder(
            UUID orderId,
            CountDownLatch bothTransactionsLoaded,
            CountDownLatch releaseUpdates
    ) {
        try {
            transaction().executeWithoutResult(status -> {
                Order order = orderRepository.findById(orderId).orElseThrow();
                bothTransactionsLoaded.countDown();
                await(releaseUpdates);
                order.markStockReserved(Instant.now());
            });
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private static boolean isOptimisticLockFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof jakarta.persistence.OptimisticLockException
                    || current instanceof org.springframework.dao.OptimisticLockingFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private Seed seedOrderWithCart(OrderStatus status, boolean lockCart) {
        return transaction().execute(transactionStatus -> {
            UUID customerId = UUID.randomUUID();
            Cart cart = Cart.create(customerId, CREATED_AT);
            cart.addCartItem(PRODUCT_ID, 1, CREATED_AT);
            if (lockCart) {
                cart.lockForCheckout(CREATED_AT.plusSeconds(1));
            }
            cartRepository.save(cart);

            Order order = Order.create(
                    cart.getId(),
                    customerId,
                    Currency.TRY,
                    List.of(new OrderLineSnapshot(PRODUCT_ID, "Test product", new BigDecimal("100.00"), 1)),
                    CREATED_AT
            );
            if (status == OrderStatus.PENDING_PAYMENT) {
                order.markStockReserved(CREATED_AT.plusSeconds(1));
            } else if (status == OrderStatus.PENDING_STOCK_CONFIRMATION) {
                order.markStockReserved(CREATED_AT.plusSeconds(1));
                order.markPaymentCompleted(CREATED_AT.plusSeconds(2));
            }
            orderRepository.save(order);
            return new Seed(order.getId(), cart.getId());
        });
    }

    private UUID seedOrder(OrderStatus status, boolean lockCart) {
        return seedOrderWithCart(status, lockCart).orderId();
    }

    private Order loadOrder(UUID orderId) {
        return transaction().execute(status -> orderRepository.findById(orderId).orElseThrow());
    }

    private Cart loadCart(UUID cartId) {
        return transaction().execute(status -> cartRepository.findById(cartId).orElseThrow());
    }

    private MessageEnvelope envelope(UUID messageId, String messageType, Object payload) {
        return new MessageEnvelope(
                messageId,
                messageType,
                CREATED_AT.plusSeconds(10),
                jsonMapper.valueToTree(payload)
        );
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrent update");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for concurrent update", exception);
        }
    }

    private record Seed(UUID orderId, UUID cartId) {}
}
