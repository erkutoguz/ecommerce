package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.Currency;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderConfirmedEvent;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderRejectedEvent;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventsListenerTest {

    private static final UUID MESSAGE_ID = UUID.fromString("70000000-0000-0000-0000-000000000003");
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000003");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaa03");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000003");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OrderSagaService orderSagaService;

    private JsonMapper jsonMapper;
    private OrderEventsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        listener = new OrderEventsListener(orderSagaService, new ConsumerUtil(jsonMapper));
    }

    @Test
    void handleOrderEvents_orderCheckoutStarted_shouldDeserializeAndDelegate() throws Exception {
        OrderCheckoutStartedEvent expectedEvent = event();
        MessageEnvelope envelope = envelope(
                OrderCheckoutStartedEvent.MESSAGE_TYPE,
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<OrderCheckoutStartedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderCheckoutStartedEvent.class);

        listener.handleOrderEvents(envelope);

        verify(orderSagaService).handleOrderCheckoutStarted(eq(envelope), eventCaptor.capture());
        assertEquals(expectedEvent, eventCaptor.getValue());
    }

    @Test
    void handleOrderEvents_orderRejected_shouldDeserializeAndDelegateExactlyOnce() {
        OrderRejectedEvent expectedEvent = new OrderRejectedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope(
                "ORDER_REJECTED_EVENT",
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<OrderRejectedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderRejectedEvent.class);

        listener.handleOrderEvents(envelope);

        verify(orderSagaService).handleOrderRejectedEvent(eq(envelope), eventCaptor.capture());
        assertEquals(ORDER_ID, eventCaptor.getValue().orderId());
    }

    @Test
    void handleOrderEvents_orderConfirmed_shouldDeserializeAndDelegate() {
        OrderConfirmedEvent expectedEvent = new OrderConfirmedEvent(ORDER_ID);
        MessageEnvelope envelope = envelope(
                "ORDER_CONFIRMED_EVENT",
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<OrderConfirmedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderConfirmedEvent.class);

        listener.handleOrderEvents(envelope);

        verify(orderSagaService).handleOrderConfirmedEvent(eq(envelope), eventCaptor.capture());
        assertEquals(expectedEvent, eventCaptor.getValue());
    }

    @Test
    void handleOrderEvents_unsupportedMessageType_shouldRejectMessage() {
        MessageEnvelope envelope = envelope(
                "SOME_OTHER_EVENT",
                jsonMapper.createObjectNode()
        );

        assertThrows(IllegalArgumentException.class, () -> listener.handleOrderEvents(envelope));

        verify(orderSagaService, never()).handleOrderCheckoutStarted(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void handleOrderEvents_missingPayload_shouldRejectMalformedEnvelope() {
        MessageEnvelope envelope = envelope(OrderCheckoutStartedEvent.MESSAGE_TYPE, null);

        assertThrows(IllegalArgumentException.class, () -> listener.handleOrderEvents(envelope));
        verify(orderSagaService, never()).handleOrderCheckoutStarted(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
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

    private static MessageEnvelope envelope(
            String messageType,
            tools.jackson.databind.JsonNode payload
    ) {
        return new MessageEnvelope(MESSAGE_ID, messageType, OCCURRED_AT, payload);
    }
}
