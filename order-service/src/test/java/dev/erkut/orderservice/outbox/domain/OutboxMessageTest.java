package dev.erkut.orderservice.outbox.domain;

import dev.erkut.orderservice.outbox.domain.exception.InvalidOutboxMessageException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxMessageTest {

    private static final UUID AGGREGATE_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final JsonNode PAYLOAD = new JsonMapper().readTree(
            "{\"orderId\":\"80000000-0000-0000-0000-000000000001\"}"
    );

    @Test
    void create_shouldCreatePendingOutboxMessage() {
        OutboxMessage message = OutboxMessage.create(
                AGGREGATE_ID,
                OutboxMessageType.ORDER_CHECKOUT_STARTED,
                PAYLOAD,
                CREATED_AT
        );

        assertNotNull(message.getId());
        assertEquals(AGGREGATE_ID, message.getAggregateId());
        assertEquals(OutboxMessageType.ORDER_CHECKOUT_STARTED, message.getMessageType());
        assertEquals(PAYLOAD, message.getPayload());
        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertEquals(CREATED_AT, message.getCreatedAt());
        assertNull(message.getPublishedAt());
    }

    @Test
    void create_nullAggregateId_shouldThrowInvalidOutboxMessageException() {
        assertThrows(InvalidOutboxMessageException.class, () ->
                OutboxMessage.create(null, OutboxMessageType.ORDER_CHECKOUT_STARTED, PAYLOAD, CREATED_AT)
        );
    }

    @Test
    void create_nullMessageType_shouldThrowInvalidOutboxMessageException() {
        assertThrows(InvalidOutboxMessageException.class, () ->
                OutboxMessage.create(AGGREGATE_ID, null, PAYLOAD, CREATED_AT)
        );
    }

    @Test
    void create_nullPayload_shouldThrowInvalidOutboxMessageException() {
        assertThrows(InvalidOutboxMessageException.class, () ->
                OutboxMessage.create(AGGREGATE_ID, OutboxMessageType.ORDER_CHECKOUT_STARTED, null, CREATED_AT)
        );
    }

    @Test
    void create_nullNow_shouldThrowInvalidOutboxMessageException() {
        assertThrows(InvalidOutboxMessageException.class, () ->
                OutboxMessage.create(AGGREGATE_ID, OutboxMessageType.ORDER_CHECKOUT_STARTED, PAYLOAD, null)
        );
    }
}
