package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.domain.exception.InvalidOutboxMessageException;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderservice.observability.tracing.OutboxTraceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final UUID CUSTOMER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private JsonMapper jsonMapper;

    @Mock
    private OutboxTraceContext outboxTraceContext;

    @InjectMocks
    private OutboxService outboxService;

    @Test
    void createOrderCheckoutStartedMessage_shouldSerializeAndSaveMessage() throws JacksonException {
        OrderCheckoutStartedEvent event = event();
        JsonNode payload = new JsonMapper().valueToTree(event);
        when(jsonMapper.valueToTree(event)).thenReturn(payload);
        ArgumentCaptor<OutboxMessage> messageCaptor = ArgumentCaptor.forClass(OutboxMessage.class);

        outboxService.createOrderCheckoutStartedMessage(event, CREATED_AT);

        verify(outboxMessageRepository).save(messageCaptor.capture());
        OutboxMessage saved = messageCaptor.getValue();
        assertEquals(ORDER_ID, saved.getAggregateId());
        assertEquals(OutboxMessageType.ORDER_CHECKOUT_STARTED, saved.getMessageType());
        assertEquals(payload, saved.getPayload());
        assertEquals(OutboxStatus.PENDING, saved.getStatus());
    }

    @Test
    void createOrderCheckoutStartedMessage_shouldPersistCapturedTraceContext() throws JacksonException {
        OrderCheckoutStartedEvent event = event();
        JsonNode payload = new JsonMapper().valueToTree(event);
        when(jsonMapper.valueToTree(event)).thenReturn(payload);
        when(outboxTraceContext.capture()).thenReturn(new OutboxTraceContext.Headers(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "vendor=value"
        ));
        ArgumentCaptor<OutboxMessage> messageCaptor = ArgumentCaptor.forClass(OutboxMessage.class);

        outboxService.createOrderCheckoutStartedMessage(event, CREATED_AT);

        verify(outboxMessageRepository).save(messageCaptor.capture());
        assertEquals("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                messageCaptor.getValue().getTraceparent());
        assertEquals("vendor=value", messageCaptor.getValue().getTracestate());
    }

    @Test
    void createOrderCheckoutStartedMessage_nullEvent_shouldThrowInvalidOutboxMessageException() {
        assertThrows(
                InvalidOutboxMessageException.class,
                () -> outboxService.createOrderCheckoutStartedMessage(null, CREATED_AT)
        );

        verify(outboxMessageRepository, never()).save(any(OutboxMessage.class));
    }

    @Test
    void createOrderCheckoutStartedMessage_serializationFailure_shouldThrowOutboxSerializationException()
            throws JacksonException {
        JacksonException serializationFailure = org.mockito.Mockito.mock(JacksonException.class);
        when(jsonMapper.valueToTree(any(OrderCheckoutStartedEvent.class)))
                .thenThrow(serializationFailure);

        assertThrows(
                OutboxSerializationException.class,
                () -> outboxService.createOrderCheckoutStartedMessage(event(), CREATED_AT)
        );

        verify(outboxMessageRepository, never()).save(any(OutboxMessage.class));
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
