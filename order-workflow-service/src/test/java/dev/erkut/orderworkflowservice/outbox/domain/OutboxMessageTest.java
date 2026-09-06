package dev.erkut.orderworkflowservice.outbox.domain;

import dev.erkut.orderworkflowservice.outbox.domain.exception.InvalidOutboxMessageException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OutboxMessageTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000004");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void create_shouldGenerateIndependentMessageIdAndPendingState() {
        OutboxMessage message = OutboxMessage.create(
                ORDER_ID,
                OutboxMessageType.RESERVE_STOCK_COMMAND,
                new JsonMapper().createObjectNode(),
                CREATED_AT
        );

        assertNotEquals(ORDER_ID, message.getId());
        assertEquals(ORDER_ID, message.getAggregateId());
        assertEquals(OutboxStatus.PENDING, message.getStatus());
        assertEquals(CREATED_AT, message.getCreatedAt());
    }

    @Test
    void create_nullPayload_shouldRejectMessage() {
        assertThrows(
                InvalidOutboxMessageException.class,
                () -> OutboxMessage.create(
                        ORDER_ID,
                        OutboxMessageType.RESERVE_STOCK_COMMAND,
                        null,
                        CREATED_AT
                )
        );
    }
}
