package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.Currency;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
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
        listener = new OrderEventsListener(jsonMapper, orderSagaService);
    }

    @Test
    void listenOrder_orderCheckoutStarted_shouldDeserializeAndDelegate() throws Exception {
        OrderCheckoutStartedEvent expectedEvent = event();
        MessageEnvelope envelope = envelope(
                OrderCheckoutStartedEvent.MESSAGE_TYPE,
                jsonMapper.valueToTree(expectedEvent)
        );
        ArgumentCaptor<OrderCheckoutStartedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderCheckoutStartedEvent.class);

        listener.listenOrder(envelope);

        verify(orderSagaService).handleOrderCheckoutStarted(eq(envelope), eventCaptor.capture());
        assertEquals(expectedEvent, eventCaptor.getValue());
    }

    @Test
    void listenOrder_unrelatedValidMessageType_shouldIgnore() throws Exception {
        MessageEnvelope envelope = envelope(
                "SOME_OTHER_EVENT",
                jsonMapper.createObjectNode()
        );

        listener.listenOrder(envelope);

        verify(orderSagaService, never()).handleOrderCheckoutStarted(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void listenOrder_missingPayload_shouldRejectMalformedEnvelope() {
        MessageEnvelope envelope = envelope(OrderCheckoutStartedEvent.MESSAGE_TYPE, null);

        assertThrows(IllegalArgumentException.class, () -> listener.listenOrder(envelope));
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
