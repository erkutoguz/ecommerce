package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.OrderRejectionReason;
import dev.erkut.orderworkflowservice.message.command.ProcessPaymentCommand;
import dev.erkut.orderworkflowservice.message.command.RejectOrderCommand;
import dev.erkut.orderworkflowservice.message.event.StockReservedEvent;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailedEvent;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailureReason;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxStatus;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaState;
import dev.erkut.orderworkflowservice.saga.domain.exception.IllegalOrderSagaStateException;
import dev.erkut.orderworkflowservice.saga.application.exception.OrderSagaNotFoundException;
import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderSagaStockFailureIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000020");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa20");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000020");
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

    @ParameterizedTest
    @EnumSource(StockReservationFailureReason.class)
    void handleStockReservationFailed_shouldMapReasonMoveSagaAndCreateRejectOrderOutbox(
            StockReservationFailureReason reason
    ) throws Exception {
        orderSagaRepository.save(newSaga());
        MessageEnvelope envelope = envelope(
                UUID.fromString("70000000-0000-0000-0000-000000000020"),
                reason
        );

        orderSagaService.handleStockReservationFailedEvent(envelope, event(envelope));

        OutboxMessage outbox = outboxRepository.findAll().getFirst();
        RejectOrderCommand command = jsonMapper.treeToValue(
                outbox.getPayload(),
                RejectOrderCommand.class
        );

        assertEquals(1, inboxRepository.count());
        assertEquals(OrderSagaState.ORDER_REJECTION_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertEquals(1, outboxRepository.count());
        assertEquals(OutboxMessageType.REJECT_ORDER_COMMAND, outbox.getMessageType());
        assertEquals(ORDER_ID, outbox.getAggregateId());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(ORDER_ID, command.orderId());
        assertEquals(OrderRejectionReason.OUT_OF_STOCK, command.rejectionReason());
    }

    @Test
    void handleStockReserved_shouldMoveSagaToPaymentPendingAndCreatePaymentCommand() throws Exception {
        BigDecimal totalAmount = new BigDecimal("1234.56");
        OrderSaga saga = OrderSaga.start(
                ORDER_ID,
                totalAmount,
                dev.erkut.orderworkflowservice.saga.domain.Currency.EUR,
                CUSTOMER_ID,
                OCCURRED_AT
        );
        orderSagaRepository.save(saga);
        UUID messageId = UUID.fromString("70000000-0000-0000-0000-000000000024");
        StockReservedEvent event = new StockReservedEvent(ORDER_ID, OCCURRED_AT.plusSeconds(1));
        MessageEnvelope envelope = new MessageEnvelope(
                messageId,
                "STOCK_RESERVED_EVENT",
                OCCURRED_AT.plusSeconds(1),
                jsonMapper.valueToTree(event)
        );

        orderSagaService.handleStockReservedEvent(envelope, event);

        OrderSaga updatedSaga = orderSagaRepository.findById(ORDER_ID).orElseThrow();
        OutboxMessage outbox = outboxRepository.findAll().getFirst();
        ProcessPaymentCommand command = jsonMapper.treeToValue(
                outbox.getPayload(),
                ProcessPaymentCommand.class
        );
        assertEquals(1, inboxRepository.count());
        assertEquals(OrderSagaState.PAYMENT_PENDING, updatedSaga.getState());
        assertNotEquals(OCCURRED_AT, updatedSaga.getUpdatedAt());
        assertEquals(OutboxMessageType.PROCESS_PAYMENT_COMMAND, outbox.getMessageType());
        assertEquals(ORDER_ID, outbox.getAggregateId());
        assertEquals(OutboxStatus.PENDING, outbox.getStatus());
        assertEquals(ORDER_ID, command.orderId());
        assertEquals(totalAmount, command.totalAmount());
        assertEquals(dev.erkut.orderworkflowservice.message.command.Currency.EUR, command.currency());
    }

    @Test
    void handleStockReservationFailed_sameMessageIdTwice_shouldBeIdempotent() {
        orderSagaRepository.save(newSaga());
        MessageEnvelope envelope = envelope(
                UUID.fromString("70000000-0000-0000-0000-000000000021"),
                StockReservationFailureReason.INSUFFICIENT_STOCK
        );
        StockReservationFailedEvent event = event(envelope);

        orderSagaService.handleStockReservationFailedEvent(envelope, event);
        orderSagaService.handleStockReservationFailedEvent(envelope, event);

        assertEquals(1, inboxRepository.count());
        assertEquals(OrderSagaState.ORDER_REJECTION_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
        assertEquals(1, outboxRepository.count());
    }

    @Test
    void handleStockReservationFailed_illegalSagaState_shouldRollbackInboxAndOutbox() {
        OrderSaga saga = newSaga();
        saga.markOrderRejectionPending(OCCURRED_AT);
        orderSagaRepository.save(saga);
        MessageEnvelope envelope = envelope(
                UUID.fromString("70000000-0000-0000-0000-000000000022"),
                StockReservationFailureReason.ITEM_INACTIVE
        );

        assertThrows(
                IllegalOrderSagaStateException.class,
                () -> orderSagaService.handleStockReservationFailedEvent(envelope, event(envelope))
        );

        assertEquals(0, inboxRepository.count());
        assertEquals(0, outboxRepository.count());
        assertEquals(OrderSagaState.ORDER_REJECTION_PENDING,
                orderSagaRepository.findById(ORDER_ID).orElseThrow().getState());
    }

    @Test
    void handleStockReservationFailed_missingSaga_shouldRollbackInboxAndOutbox() {
        MessageEnvelope envelope = envelope(
                UUID.fromString("70000000-0000-0000-0000-000000000023"),
                StockReservationFailureReason.ITEM_NOT_FOUND
        );

        assertThrows(
                OrderSagaNotFoundException.class,
                () -> orderSagaService.handleStockReservationFailedEvent(envelope, event(envelope))
        );

        assertEquals(0, inboxRepository.count());
        assertEquals(0, outboxRepository.count());
    }

    private MessageEnvelope envelope(UUID messageId, StockReservationFailureReason reason) {
        StockReservationFailedEvent event = new StockReservationFailedEvent(
                ORDER_ID,
                reason,
                PRODUCT_ID
        );
        return new MessageEnvelope(
                messageId,
                "STOCK_RESERVATION_FAILED_EVENT",
                OCCURRED_AT,
                jsonMapper.valueToTree(event)
        );
    }

    private StockReservationFailedEvent event(MessageEnvelope envelope) {
        return jsonMapper.convertValue(envelope.payload(), StockReservationFailedEvent.class);
    }

    private static OrderSaga newSaga() {
        return OrderSaga.start(
                ORDER_ID,
                new BigDecimal("200.00"),
                dev.erkut.orderworkflowservice.saga.domain.Currency.TRY,
                CUSTOMER_ID,
                OCCURRED_AT
        );
    }
}
