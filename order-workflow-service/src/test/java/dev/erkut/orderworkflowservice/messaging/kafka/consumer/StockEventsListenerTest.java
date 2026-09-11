package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationFailedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationFailureReason;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationConfirmedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationReleasedEvent;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservedEvent;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StockEventsListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000010");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000010");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000010");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OrderSagaService orderSagaService;

    private JsonMapper jsonMapper;
    private StockEventsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        listener = new StockEventsListener(
                orderSagaService,
                new ConsumerUtil(jsonMapper)
        );
    }

    @Test
    void handleStockEvents_stockReservationFailed_shouldDeserializeAndDelegate() {
        StockReservationFailedEvent expectedEvent = new StockReservationFailedEvent(
                ORDER_ID,
                StockReservationFailureReason.INSUFFICIENT_STOCK,
                PRODUCT_ID
        );
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID,
                "STOCK_RESERVATION_FAILED_EVENT",
                OCCURRED_AT,
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<StockReservationFailedEvent> eventCaptor =
                ArgumentCaptor.forClass(StockReservationFailedEvent.class);

        listener.handleStockEvents(envelope);

        verify(orderSagaService).handleStockReservationFailedEvent(
                eq(envelope),
                eventCaptor.capture()
        );
        assertEquals(expectedEvent, eventCaptor.getValue());
    }

    @Test
    void handleStockEvents_stockReserved_shouldDeserializeAndDelegate() {
        StockReservedEvent expectedEvent = new StockReservedEvent(ORDER_ID, OCCURRED_AT);
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID,
                "STOCK_RESERVED_EVENT",
                OCCURRED_AT,
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<StockReservedEvent> eventCaptor =
                ArgumentCaptor.forClass(StockReservedEvent.class);

        listener.handleStockEvents(envelope);

        verify(orderSagaService).handleStockReservedEvent(
                eq(envelope),
                eventCaptor.capture()
        );
        assertEquals(expectedEvent, eventCaptor.getValue());
    }

    @Test
    void handleStockEvents_stockReservationConfirmed_shouldDeserializeAndDelegate() {
        StockReservationConfirmedEvent expectedEvent = new StockReservationConfirmedEvent(ORDER_ID);
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID,
                "STOCK_RESERVATION_CONFIRMED_EVENT",
                OCCURRED_AT,
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<StockReservationConfirmedEvent> eventCaptor =
                ArgumentCaptor.forClass(StockReservationConfirmedEvent.class);

        listener.handleStockEvents(envelope);

        verify(orderSagaService).handleStockReservationConfirmedEvent(
                eq(envelope),
                eventCaptor.capture()
        );
        assertEquals(expectedEvent, eventCaptor.getValue());
    }

    @Test
    void handleStockEvents_stockReservationReleased_shouldDeserializeAndDelegate() {
        StockReservationReleasedEvent expectedEvent = new StockReservationReleasedEvent(ORDER_ID);
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID,
                "STOCK_RESERVATION_RELEASED_EVENT",
                OCCURRED_AT,
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<StockReservationReleasedEvent> eventCaptor =
                ArgumentCaptor.forClass(StockReservationReleasedEvent.class);

        listener.handleStockEvents(envelope);

        verify(orderSagaService).handleStockReservationReleasedEvent(
                eq(envelope),
                eventCaptor.capture()
        );
        assertEquals(expectedEvent, eventCaptor.getValue());
    }
}
