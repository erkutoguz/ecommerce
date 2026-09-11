package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.ordercommands.OrderRejectionReason;
import dev.erkut.orderworkflowservice.message.command.ordercommands.RejectOrderCommand;
import dev.erkut.orderworkflowservice.message.command.stockcommands.ReleaseStockReservationCommand;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentFailedEvent;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentFailureReason;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationReleasedEvent;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderworkflowservice.saga.domain.Currency;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaFailureReason;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderSagaPaymentFailureIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000070");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa70");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

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
    void paymentFailure_shouldPersistReasonMoveSagaToReleasePendingAndCreateReleaseCommand() throws Exception {
        OrderSaga saga = paymentPendingSaga();
        orderSagaRepository.save(saga);
        PaymentFailedEvent event = new PaymentFailedEvent(
                ORDER_ID,
                PaymentFailureReason.SESSION_EXPIRED
        );
        UUID messageId = UUID.randomUUID();

        orderSagaService.handlePaymentFailedEvent(envelope(messageId, event), event);

        OrderSaga persistedSaga = orderSagaRepository.findById(ORDER_ID).orElseThrow();
        OutboxMessage outbox = outboxRepository.findAll().getFirst();
        ReleaseStockReservationCommand command = jsonMapper.treeToValue(
                outbox.getPayload(), ReleaseStockReservationCommand.class);

        assertEquals(OrderSagaState.STOCK_RELEASE_PENDING, persistedSaga.getState());
        assertEquals(OrderSagaFailureReason.PAYMENT_EXPIRED, persistedSaga.getFailureReason());
        assertEquals(1, inboxRepository.count());
        assertEquals(1, outboxRepository.count());
        assertEquals(OutboxMessageType.RELEASE_STOCK_RESERVATION_COMMAND, outbox.getMessageType());
        assertEquals(ORDER_ID, outbox.getAggregateId());
        assertEquals(ORDER_ID, command.orderId());
        assertEquals(1, outbox.getPayload().size());
    }

    @Test
    void paymentFailure_duplicateMessageId_shouldNotCreateSecondReleaseCommand() {
        orderSagaRepository.save(paymentPendingSaga());
        PaymentFailedEvent event = new PaymentFailedEvent(
                ORDER_ID,
                PaymentFailureReason.SESSION_EXPIRED
        );
        MessageEnvelope envelope = envelope(UUID.randomUUID(), event);

        orderSagaService.handlePaymentFailedEvent(envelope, event);
        orderSagaService.handlePaymentFailedEvent(envelope, event);

        assertEquals(1, inboxRepository.count());
        assertEquals(1, outboxRepository.count());
        assertEquals(OrderSagaState.STOCK_RELEASE_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
    }

    @Test
    void stockReleased_shouldMoveReleasePendingSagaToRejectionPendingAndCreateRejectCommand() throws Exception {
        OrderSaga saga = paymentPendingSaga();
        saga.markStockReservationReleasePending(
                CREATED_AT.plusSeconds(2),
                OrderSagaFailureReason.PAYMENT_EXPIRED
        );
        orderSagaRepository.save(saga);
        StockReservationReleasedEvent event = new StockReservationReleasedEvent(ORDER_ID);
        UUID messageId = UUID.randomUUID();

        orderSagaService.handleStockReservationReleasedEvent(envelope(messageId, event), event);

        OrderSaga persistedSaga = orderSagaRepository.findById(ORDER_ID).orElseThrow();
        OutboxMessage outbox = outboxRepository.findAll().getFirst();
        RejectOrderCommand command = jsonMapper.treeToValue(outbox.getPayload(), RejectOrderCommand.class);

        assertEquals(OrderSagaState.ORDER_REJECTION_PENDING, persistedSaga.getState());
        assertEquals(OrderRejectionReason.PAYMENT_EXPIRED, command.rejectionReason());
        assertEquals(1, inboxRepository.count());
        assertEquals(1, outboxRepository.count());
        assertEquals(OutboxMessageType.REJECT_ORDER_COMMAND, outbox.getMessageType());
        assertEquals(ORDER_ID, outbox.getAggregateId());
        assertEquals(ORDER_ID, command.orderId());
    }

    @Test
    void stockReleased_duplicateMessageId_shouldNotCreateSecondRejectCommand() {
        OrderSaga saga = paymentPendingSaga();
        saga.markStockReservationReleasePending(
                CREATED_AT.plusSeconds(2),
                OrderSagaFailureReason.PAYMENT_EXPIRED
        );
        orderSagaRepository.save(saga);
        StockReservationReleasedEvent event = new StockReservationReleasedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope(UUID.randomUUID(), event);

        orderSagaService.handleStockReservationReleasedEvent(envelope, event);
        orderSagaService.handleStockReservationReleasedEvent(envelope, event);

        assertEquals(1, inboxRepository.count());
        assertEquals(1, outboxRepository.count());
        assertEquals(OrderSagaState.ORDER_REJECTION_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
    }

    @Test
    void stockReleased_fromPaymentPending_shouldRollbackInboxAndCreateNoCommand() {
        orderSagaRepository.save(paymentPendingSaga());
        StockReservationReleasedEvent event = new StockReservationReleasedEvent(ORDER_ID);
        UUID messageId = UUID.randomUUID();

        assertThrows(IllegalOrderSagaStateException.class,
                () -> orderSagaService.handleStockReservationReleasedEvent(
                        envelope(messageId, event), event));

        assertEquals(OrderSagaState.PAYMENT_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, outboxRepository.count());
    }

    private OrderSaga paymentPendingSaga() {
        OrderSaga saga = OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                Currency.TRY,
                CUSTOMER_ID,
                CREATED_AT
        );
        saga.markPaymentPending(CREATED_AT.plusSeconds(1));
        return saga;
    }

    private MessageEnvelope envelope(UUID messageId, Object event) {
        return new MessageEnvelope(
                messageId,
                event instanceof PaymentFailedEvent
                        ? "PAYMENT_FAILED_EVENT"
                        : "STOCK_RESERVATION_RELEASED_EVENT",
                CREATED_AT.plusSeconds(3),
                jsonMapper.valueToTree(event)
        );
    }
}
