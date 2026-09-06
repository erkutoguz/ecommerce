package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.domain.InboxMessage;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.ReserveStockCommand;
import dev.erkut.orderworkflowservice.message.event.Currency;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxStatus;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaState;
import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderSagaServiceIntegrationTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired
    private OrderSagaService orderSagaService;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private OrderSagaRepository orderSagaRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        orderSagaRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    @Test
    void handleOrderCheckoutStarted_shouldPersistInboxSagaAndOutboxWithCorrectIdentityMapping()
            throws Exception {
        OrderCheckoutStartedEvent event = event();
        MessageEnvelope envelope = envelope(MESSAGE_ID, event);

        orderSagaService.handleOrderCheckoutStarted(envelope, event);

        InboxMessage inbox = inboxRepository.findById(MESSAGE_ID).orElseThrow();
        OrderSaga saga = orderSagaRepository.findById(ORDER_ID).orElseThrow();
        OutboxMessage outbox = outboxRepository.findAll().getFirst();
        ReserveStockCommand command = jsonMapper.treeToValue(
                outbox.getPayload(),
                ReserveStockCommand.class
        );

        assertEquals(1, inboxRepository.count());
        assertEquals(1, orderSagaRepository.count());
        assertEquals(1, outboxRepository.count());

        assertEquals(MESSAGE_ID, inbox.getMessageId());
        assertEquals(OrderCheckoutStartedEvent.MESSAGE_TYPE, inbox.getMessageType());
        assertEquals(ORDER_ID, inbox.getAggregateId());
        assertNotNull(inbox.getProcessedAt());

        assertEquals(ORDER_ID, saga.getOrderId());
        assertEquals(CUSTOMER_ID, saga.getCustomerId());
        assertEquals(OrderSagaState.STOCK_RESERVATION_PENDING, saga.getState());
        assertNotNull(saga.getCreatedAt());
        assertNotNull(saga.getUpdatedAt());

        assertNotEquals(MESSAGE_ID, outbox.getId());
        assertEquals(ORDER_ID, outbox.getAggregateId());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(OutboxMessageType.RESERVE_STOCK_COMMAND, outbox.getMessageType());
        assertNotNull(outbox.getPayload());
        assertNotNull(outbox.getCreatedAt());
        assertNull(outbox.getPublishedAt());

        assertEquals(ORDER_ID, command.orderId());
        assertEquals(
                List.of(new ReserveStockCommand.ReserveStockItem(PRODUCT_ID, 2)),
                command.items()
        );
    }

    @Test
    void handleOrderCheckoutStarted_sameMessageTwice_shouldProcessOnlyOnce() {
        OrderCheckoutStartedEvent event = event();
        MessageEnvelope envelope = envelope(MESSAGE_ID, event);

        orderSagaService.handleOrderCheckoutStarted(envelope, event);
        orderSagaService.handleOrderCheckoutStarted(envelope, event);

        assertEquals(1, inboxRepository.count());
        assertEquals(1, orderSagaRepository.count());
        assertEquals(1, outboxRepository.count());
    }

    @Test
    void handleOrderCheckoutStarted_concurrentDuplicateDelivery_shouldProcessOnlyOnce()
            throws Exception {
        int deliveryCount = 6;
        OrderCheckoutStartedEvent event = event();
        MessageEnvelope envelope = envelope(MESSAGE_ID, event);
        CountDownLatch ready = new CountDownLatch(deliveryCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(deliveryCount);
        List<Future<Void>> deliveries = new ArrayList<>();

        try {
            for (int index = 0; index < deliveryCount; index++) {
                deliveries.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start duplicate delivery");
                    }
                    orderSagaService.handleOrderCheckoutStarted(envelope, event);
                    return null;
                }));
            }

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            for (Future<Void> delivery : deliveries) {
                delivery.get(20, TimeUnit.SECONDS);
            }
        } finally {
            start.countDown();
            executor.shutdownNow();
        }

        assertEquals(1, inboxRepository.count());
        assertEquals(1, orderSagaRepository.count());
        assertEquals(1, outboxRepository.count());
    }

    private MessageEnvelope envelope(UUID messageId, OrderCheckoutStartedEvent event) {
        JsonNode payload = jsonMapper.valueToTree(event);
        return new MessageEnvelope(
                messageId,
                OrderCheckoutStartedEvent.MESSAGE_TYPE,
                OCCURRED_AT,
                payload
        );
    }

    private static OrderCheckoutStartedEvent event() {
        return new OrderCheckoutStartedEvent(
                ORDER_ID,
                CUSTOMER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                List.of(new OrderCheckoutStartedEvent.OrderCheckoutItem(PRODUCT_ID, 2))
        );
    }
}
