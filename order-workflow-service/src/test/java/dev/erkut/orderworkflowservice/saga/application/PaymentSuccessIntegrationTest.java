package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.stockcommands.ConfirmStockReservationCommand;
import dev.erkut.orderworkflowservice.message.command.ordercommands.MarkOrderPaymentCompletedCommand;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentCompletedEvent;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentSuccessIntegrationTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000060");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000060");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa60");
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
    void handlePaymentCompleted_shouldTransitionSagaAndCreateMinimalConfirmCommand() throws Exception {
        OrderSaga saga = paymentPendingSaga();
        orderSagaRepository.save(saga);
        PaymentCompletedEvent event = new PaymentCompletedEvent(ORDER_ID);

        orderSagaService.handlePaymentCompletedEvent(envelope(MESSAGE_ID, event), event);

        OrderSaga persistedSaga = orderSagaRepository.findById(ORDER_ID).orElseThrow();
        OutboxMessage outbox = outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType() == OutboxMessageType.CONFIRM_STOCK_RESERVATION_COMMAND)
                .findFirst()
                .orElseThrow();
        ConfirmStockReservationCommand command = jsonMapper.treeToValue(
                outbox.getPayload(),
                ConfirmStockReservationCommand.class
        );

        assertEquals(1, inboxRepository.count());
        assertEquals(OrderSagaState.STOCK_CONFIRMATION_PENDING, persistedSaga.getState());
        assertEquals(2, outboxRepository.count());
        assertEquals(ORDER_ID, outbox.getAggregateId());
        assertEquals(OutboxMessageType.CONFIRM_STOCK_RESERVATION_COMMAND, outbox.getMessageType());
        assertEquals(1, outbox.getPayload().size());
        assertEquals(ORDER_ID, command.orderId());
        OutboxMessage marker = outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType() == OutboxMessageType.MARK_ORDER_PAYMENT_COMPLETED_COMMAND)
                .findFirst()
                .orElseThrow();
        assertEquals(ORDER_ID, marker.getAggregateId());
        assertEquals(ORDER_ID, jsonMapper.treeToValue(
                marker.getPayload(), MarkOrderPaymentCompletedCommand.class
        ).orderId());
    }

    @Test
    void handlePaymentCompleted_duplicateMessageId_shouldNotTransitionOrCreateSecondOutbox() {
        OrderSaga saga = paymentPendingSaga();
        orderSagaRepository.save(saga);
        PaymentCompletedEvent event = new PaymentCompletedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope(MESSAGE_ID, event);

        orderSagaService.handlePaymentCompletedEvent(envelope, event);
        orderSagaService.handlePaymentCompletedEvent(envelope, event);

        assertEquals(1, inboxRepository.count());
        assertEquals(OrderSagaState.STOCK_CONFIRMATION_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertEquals(2, outboxRepository.count());
    }

    @Test
    void handlePaymentCompleted_fromInvalidState_shouldRollbackInboxAndCreateNoOutbox() {
        OrderSaga saga = OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                CUSTOMER_ID,
                INITIAL_AT
        );
        orderSagaRepository.save(saga);
        PaymentCompletedEvent event = new PaymentCompletedEvent(ORDER_ID);

        assertThrows(
                IllegalOrderSagaStateException.class,
                () -> orderSagaService.handlePaymentCompletedEvent(envelope(MESSAGE_ID, event), event)
        );

        assertEquals(OrderSagaState.STOCK_RESERVATION_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertEquals(0, inboxRepository.count());
        assertEquals(0, outboxRepository.count());
    }

    private OrderSaga paymentPendingSaga() {
        OrderSaga saga = OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                CUSTOMER_ID,
                INITIAL_AT
        );
        saga.markPaymentPending(INITIAL_AT.plusSeconds(1));
        return saga;
    }

    private MessageEnvelope envelope(UUID messageId, PaymentCompletedEvent event) {
        return new MessageEnvelope(
                messageId,
                "PAYMENT_COMPLETED_EVENT",
                INITIAL_AT.plusSeconds(2),
                jsonMapper.valueToTree(event)
        );
    }
}
