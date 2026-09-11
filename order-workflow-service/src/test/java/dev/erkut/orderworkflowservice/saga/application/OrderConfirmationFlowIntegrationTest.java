package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.TestcontainersConfiguration;
import dev.erkut.orderworkflowservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.command.ordercommands.ConfirmOrderCommand;
import dev.erkut.orderworkflowservice.message.command.stockcommands.ConfirmStockReservationCommand;
import dev.erkut.orderworkflowservice.message.command.InitiatePaymentCommand;
import dev.erkut.orderworkflowservice.message.command.ordercommands.MarkOrderPaymentCompletedCommand;
import dev.erkut.orderworkflowservice.message.command.ordercommands.MarkOrderStockReservedCommand;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderConfirmedEvent;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentCompletedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationConfirmedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservedEvent;
import dev.erkut.orderworkflowservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.orderworkflowservice.messaging.kafka.routing.KafkaTopicResolver;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OrderConfirmationFlowIntegrationTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-00000000070a");
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

    private final KafkaTopicResolver topicResolver = new KafkaTopicResolver(new KafkaTopicsProperties(
            "order.events",
            "stock.commands",
            "stock.events",
            "payment.commands",
            "payment.events",
            "payment.events.DLT",
            "order.commands",
            "order.events.DLT",
            "stock.events.DLT"
    ));

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAll();
        orderSagaRepository.deleteAll();
        inboxRepository.deleteAll();
    }

    @Test
    void stockReserved_shouldTransitionSagaAndCreateOrderAndPaymentCommands() throws Exception {
        orderSagaRepository.save(startSaga());
        StockReservedEvent event = new StockReservedEvent(ORDER_ID, CREATED_AT.plusSeconds(1));

        orderSagaService.handleStockReservedEvent(
                envelope("STOCK_RESERVED_EVENT", event),
                event
        );

        assertEquals(OrderSagaState.PAYMENT_PENDING, loadSaga().getState());
        Map<OutboxMessageType, OutboxMessage> messages = outboxByType();
        assertEquals(2, messages.size());

        OutboxMessage markMessage = messages.get(OutboxMessageType.MARK_ORDER_STOCK_RESERVED_COMMAND);
        MarkOrderStockReservedCommand markCommand = jsonMapper.treeToValue(
                markMessage.getPayload(),
                MarkOrderStockReservedCommand.class
        );
        assertEquals(ORDER_ID, markMessage.getAggregateId());
        assertEquals(ORDER_ID, markCommand.orderId());
        assertEquals("order.commands", topicResolver.resolve(markMessage.getMessageType()));

        OutboxMessage paymentMessage = messages.get(OutboxMessageType.INITIATE_PAYMENT_COMMAND);
        InitiatePaymentCommand paymentCommand = jsonMapper.treeToValue(
                paymentMessage.getPayload(),
                InitiatePaymentCommand.class
        );
        assertEquals(ORDER_ID, paymentMessage.getAggregateId());
        assertEquals(ORDER_ID, paymentCommand.orderId());
        assertEquals("payment.commands", topicResolver.resolve(paymentMessage.getMessageType()));
    }

    @Test
    void paymentCompleted_shouldTransitionSagaAndCreateOrderAndStockCommands() throws Exception {
        OrderSaga saga = startSaga();
        saga.markPaymentPending(CREATED_AT.plusSeconds(1));
        orderSagaRepository.save(saga);
        PaymentCompletedEvent event = new PaymentCompletedEvent(ORDER_ID);

        orderSagaService.handlePaymentCompletedEvent(
                envelope("PAYMENT_COMPLETED_EVENT", event),
                event
        );

        assertEquals(OrderSagaState.STOCK_CONFIRMATION_PENDING, loadSaga().getState());
        Map<OutboxMessageType, OutboxMessage> messages = outboxByType();
        assertEquals(2, messages.size());

        OutboxMessage markMessage = messages.get(OutboxMessageType.MARK_ORDER_PAYMENT_COMPLETED_COMMAND);
        MarkOrderPaymentCompletedCommand markCommand = jsonMapper.treeToValue(
                markMessage.getPayload(),
                MarkOrderPaymentCompletedCommand.class
        );
        assertEquals(ORDER_ID, markMessage.getAggregateId());
        assertEquals(ORDER_ID, markCommand.orderId());
        assertEquals("order.commands", topicResolver.resolve(markMessage.getMessageType()));

        OutboxMessage stockMessage = messages.get(OutboxMessageType.CONFIRM_STOCK_RESERVATION_COMMAND);
        ConfirmStockReservationCommand stockCommand = jsonMapper.treeToValue(
                stockMessage.getPayload(),
                ConfirmStockReservationCommand.class
        );
        assertEquals(ORDER_ID, stockMessage.getAggregateId());
        assertEquals(ORDER_ID, stockCommand.orderId());
        assertEquals("stock.commands", topicResolver.resolve(stockMessage.getMessageType()));
    }

    @Test
    void stockReservationConfirmed_shouldCreateConfirmOrderCommandIdempotently() throws Exception {
        OrderSaga saga = startSaga();
        saga.markPaymentPending(CREATED_AT.plusSeconds(1));
        saga.markStockReservationConfirmationPending(CREATED_AT.plusSeconds(2));
        orderSagaRepository.save(saga);
        StockReservationConfirmedEvent event = new StockReservationConfirmedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope("STOCK_RESERVATION_CONFIRMED_EVENT", event);

        orderSagaService.handleStockReservationConfirmedEvent(envelope, event);
        orderSagaService.handleStockReservationConfirmedEvent(envelope, event);

        assertEquals(OrderSagaState.ORDER_CONFIRMATION_PENDING, loadSaga().getState());
        assertEquals(1, inboxRepository.count());
        assertEquals(1, outboxRepository.count());
        OutboxMessage message = outboxRepository.findAll().getFirst();
        ConfirmOrderCommand command = jsonMapper.treeToValue(message.getPayload(), ConfirmOrderCommand.class);
        assertEquals(OutboxMessageType.CONFIRM_ORDER_COMMAND, message.getMessageType());
        assertEquals(ORDER_ID, message.getAggregateId());
        assertEquals(ORDER_ID, command.orderId());
        assertEquals(1, message.getPayload().size());
        assertEquals("order.commands", topicResolver.resolve(message.getMessageType()));
    }

    @Test
    void orderConfirmed_shouldCompleteSagaIdempotentlyWithoutOutbox() {
        OrderSaga saga = startSaga();
        saga.markPaymentPending(CREATED_AT.plusSeconds(1));
        saga.markStockReservationConfirmationPending(CREATED_AT.plusSeconds(2));
        saga.markOrderConfirmationPending(CREATED_AT.plusSeconds(3));
        orderSagaRepository.save(saga);
        OrderConfirmedEvent event = new OrderConfirmedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope("ORDER_CONFIRMED_EVENT", event);

        orderSagaService.handleOrderConfirmedEvent(envelope, event);
        orderSagaService.handleOrderConfirmedEvent(envelope, event);

        assertEquals(OrderSagaState.COMPLETED, loadSaga().getState());
        assertEquals(1, inboxRepository.count());
        assertTrue(outboxRepository.findAll().isEmpty());
    }

    @Test
    void orderConfirmed_fromInvalidState_shouldRollbackInboxAndRejectTransition() {
        orderSagaRepository.save(startSaga());
        OrderConfirmedEvent event = new OrderConfirmedEvent(ORDER_ID);

        assertThrows(
                IllegalOrderSagaStateException.class,
                () -> orderSagaService.handleOrderConfirmedEvent(
                        envelope("ORDER_CONFIRMED_EVENT", event),
                        event
                )
        );

        assertEquals(OrderSagaState.STOCK_RESERVATION_PENDING, loadSaga().getState());
        assertEquals(0, inboxRepository.count());
        assertTrue(outboxRepository.findAll().isEmpty());
    }

    private OrderSaga startSaga() {
        return OrderSaga.start(
                ORDER_ID,
                new BigDecimal("100.00"),
                Currency.TRY,
                CUSTOMER_ID,
                CREATED_AT
        );
    }

    private OrderSaga loadSaga() {
        return orderSagaRepository.findById(ORDER_ID).orElseThrow();
    }

    private Map<OutboxMessageType, OutboxMessage> outboxByType() {
        return outboxRepository.findAll().stream()
                .collect(Collectors.toMap(OutboxMessage::getMessageType, Function.identity()));
    }

    private MessageEnvelope envelope(String messageType, Object payload) {
        return new MessageEnvelope(
                UUID.randomUUID(),
                messageType,
                CREATED_AT.plusSeconds(10),
                jsonMapper.valueToTree(payload)
        );
    }
}
