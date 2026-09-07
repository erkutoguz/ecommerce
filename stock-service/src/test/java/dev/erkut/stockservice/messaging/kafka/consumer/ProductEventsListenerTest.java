package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.stock.application.StockService;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductEventsListenerTest {

    private static final UUID MESSAGE_ID =
            UUID.fromString("70000000-0000-0000-0000-000000000001");
    private static final UUID PRODUCT_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private StockService stockService;

    private JsonMapper jsonMapper;
    private ProductEventsListener listener;

    @BeforeEach
    void setUp() {
        jsonMapper = new JsonMapper();
        listener = new ProductEventsListener(stockService, jsonMapper);
    }

    @Test
    void productCreatedEventIsDeserializedAndDelegated() throws Exception {
        MessageEnvelope envelope = envelope(
                "PRODUCT_CREATED",
                jsonMapper.valueToTree(new ProductCreatedEvent(PRODUCT_ID)));
        ArgumentCaptor<ProductCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(ProductCreatedEvent.class);

        listener.listenProductEvent(envelope);

        verify(stockService).handleProductCreated(eq(envelope), eventCaptor.capture());
        assertEquals(PRODUCT_ID, eventCaptor.getValue().productId());
    }

    @Test
    void unknownMessageTypeIsIgnored() throws Exception {
        listener.listenProductEvent(envelope("FUTURE_PRODUCT_EVENT", jsonMapper.createObjectNode()));

        verify(stockService, never()).handleProductCreated(any(), any());
    }

    @Test
    void malformedEnvelopeIsRejected() {
        MessageEnvelope envelope = new MessageEnvelope(
                MESSAGE_ID, "PRODUCT_CREATED", OCCURRED_AT, null);

        assertThrows(IllegalArgumentException.class,
                () -> listener.listenProductEvent(envelope));
        verify(stockService, never()).handleProductCreated(any(), any());
    }

    @Test
    void blankMessageTypeIsRejected() {
        MessageEnvelope envelope = envelope(" ", jsonMapper.createObjectNode());

        assertThrows(IllegalArgumentException.class,
                () -> listener.listenProductEvent(envelope));
    }

    private static MessageEnvelope envelope(String messageType,
                                            tools.jackson.databind.JsonNode payload) {
        return new MessageEnvelope(MESSAGE_ID, messageType, OCCURRED_AT, payload);
    }
}
