package dev.erkut.stockservice.outbox.application;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.stockservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import dev.erkut.stockservice.outbox.domain.OutboxMessageType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaMessagePublisher messagePublisher;

    @Mock
    private KafkaTopicResolver topicResolver;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(outboxService, messagePublisher, topicResolver);
    }

    @Test
    void successfulPublishMarksMessagePublishedAfterAck() {
        JsonMapper mapper = new JsonMapper();
        var payload = mapper.createObjectNode().put("orderId", ORDER_ID.toString());
        OutboxMessage message = OutboxMessage.create(
                ORDER_ID,
                OutboxMessageType.STOCK_RESERVED_EVENT,
                payload,
                CREATED_AT
        );
        ArgumentCaptor<MessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(MessageEnvelope.class);
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(topicResolver.resolve(OutboxMessageType.STOCK_RESERVED_EVENT)).thenReturn("stock.events");
        when(messagePublisher.publish(eq("stock.events"), eq(ORDER_ID), envelopeCaptor.capture()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.relay();

        MessageEnvelope envelope = envelopeCaptor.getValue();
        assertEquals(message.getId(), envelope.messageId());
        assertEquals(OutboxMessageType.STOCK_RESERVED_EVENT.name(), envelope.messageType());
        assertEquals(CREATED_AT, envelope.occurredAt());
        assertSame(payload, envelope.payload());
        verify(outboxService).markPublished(eq(message.getId()), any(Instant.class));
    }

    @Test
    void failedPublishDoesNotMarkMessagePublished() {
        OutboxMessage message = OutboxMessage.create(
                ORDER_ID,
                OutboxMessageType.STOCK_RESERVATION_FAILED_EVENT,
                new JsonMapper().createObjectNode(),
                CREATED_AT
        );
        CompletableFuture<SendResult<String, Object>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka unavailable"));
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(topicResolver.resolve(OutboxMessageType.STOCK_RESERVATION_FAILED_EVENT)).thenReturn("stock.events");
        when(messagePublisher.publish(eq("stock.events"), eq(ORDER_ID), any(MessageEnvelope.class)))
                .thenReturn(failedFuture);

        assertThrows(java.util.concurrent.CompletionException.class, () -> relay.relay());

        verify(outboxService, never()).markPublished(any(), any());
    }
}
