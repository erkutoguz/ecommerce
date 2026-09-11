package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderRejectedEvent;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderworkflowservice.saga.application.exception.OrderSagaNotFoundException;
import dev.erkut.orderworkflowservice.saga.domain.Currency;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaState;
import dev.erkut.orderworkflowservice.saga.domain.exception.IllegalOrderSagaStateException;
import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderSagaOrderRejectedIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000040");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa40");
    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000040");
    private static final Instant INITIAL_AT = Instant.parse("2026-01-01T10:00:00Z");

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
    void handleOrderRejected_shouldCommitTerminalFailureAndIgnoreSameMessageIdRedelivery() {
        OrderSaga saga = newSaga();
        saga.markOrderRejectionPending(INITIAL_AT.plusSeconds(1));
        orderSagaRepository.save(saga);
        OrderRejectedEvent event = new OrderRejectedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope(MESSAGE_ID, event);

        orderSagaService.handleOrderRejectedEvent(envelope, event);

        OrderSaga failedSaga = orderSagaRepository.findById(ORDER_ID).orElseThrow();
        assertEquals(1, inboxRepository.count());
        assertTrue(inboxRepository.existsById(MESSAGE_ID));
        assertEquals(OrderSagaState.FAILED, failedSaga.getState());
        assertTrue(failedSaga.getUpdatedAt().isAfter(INITIAL_AT.plusSeconds(1)));
        assertEquals(0, outboxRepository.count());

        assertDoesNotThrow(() -> orderSagaService.handleOrderRejectedEvent(envelope, event));

        assertEquals(1, inboxRepository.count());
        assertEquals(OrderSagaState.FAILED,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void handleOrderRejected_missingSaga_shouldRollbackInboxAndCreateNoOutbox() {
        OrderRejectedEvent event = new OrderRejectedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope(MESSAGE_ID, event);

        assertThrows(
                OrderSagaNotFoundException.class,
                () -> orderSagaService.handleOrderRejectedEvent(envelope, event)
        );

        assertEquals(0, inboxRepository.count());
        assertFalse(inboxRepository.existsById(MESSAGE_ID));
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void handleOrderRejected_fromPaymentPending_shouldRollbackInboxAndLeaveSagaUnchanged() {
        OrderSaga saga = newSaga();
        saga.markPaymentPending(INITIAL_AT.plusSeconds(1));
        orderSagaRepository.save(saga);
        OrderRejectedEvent event = new OrderRejectedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope(MESSAGE_ID, event);

        assertThrows(
                IllegalOrderSagaStateException.class,
                () -> orderSagaService.handleOrderRejectedEvent(envelope, event)
        );

        assertEquals(OrderSagaState.PAYMENT_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertEquals(0, inboxRepository.count());
        assertFalse(inboxRepository.existsById(MESSAGE_ID));
        assertEquals(0, outboxRepository.count());
    }

    private MessageEnvelope envelope(UUID messageId, OrderRejectedEvent event) {
        return new MessageEnvelope(
                messageId,
                "ORDER_REJECTED_EVENT",
                INITIAL_AT.plusSeconds(2),
                jsonMapper.valueToTree(event)
        );
    }

    private static OrderSaga newSaga() {
        return OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                CUSTOMER_ID,
                INITIAL_AT
        );
    }
}
