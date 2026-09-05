package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final String TOPIC = "order.events";
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final JsonNode PAYLOAD = new JsonMapper().readTree(
            "{\"orderId\":\"80000000-0000-0000-0000-000000000001\"}"
    );

    @Mock
    private OutboxMessageRepository outboxMessageRepository;

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaMessagePublisher messagePublisher;

    private OutboxRelay outboxRelay;

    @BeforeEach
    void setUp() {
        outboxRelay = new OutboxRelay(
                outboxMessageRepository,
                outboxService,
                messagePublisher,
                TOPIC
        );
    }

    @Test
    void relay_shouldMarkMessagePublishedWhenKafkaSendSucceeds() {
        UUID aggregateId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        OutboxMessage message = pendingMessage(aggregateId, CREATED_AT, PAYLOAD);
        CompletableFuture<SendResult<String, MessageEnvelope>> sendFuture = CompletableFuture.completedFuture(null);
        ArgumentCaptor<MessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(MessageEnvelope.class);

        when(outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(message));
        when(messagePublisher.publish(eq(TOPIC), eq(aggregateId), envelopeCaptor.capture()))
                .thenReturn(sendFuture);

        outboxRelay.relay();

        MessageEnvelope envelope = envelopeCaptor.getValue();
        assertSame(PAYLOAD, envelope.payload());
        assertEquals(message.getId(), envelope.messageId());
        assertEquals(OutboxMessageType.ORDER_CHECKOUT_STARTED.name(), envelope.messageType());
        assertEquals(CREATED_AT, envelope.occurredAt());
        verify(outboxService).markPublished(eq(message.getId()), any(Instant.class));
    }

    @Test
    void relay_shouldNotMarkMessagePublishedWhenKafkaSendFails() {
        UUID aggregateId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        OutboxMessage message = pendingMessage(aggregateId, CREATED_AT, PAYLOAD);
        RuntimeException failure = new RuntimeException("Kafka unavailable");
        CompletableFuture<SendResult<String, MessageEnvelope>> sendFuture = new CompletableFuture<>();
        sendFuture.completeExceptionally(failure);

        when(outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(message));
        when(messagePublisher.publish(eq(TOPIC), eq(aggregateId), any(MessageEnvelope.class)))
                .thenReturn(sendFuture);

        CompletionException exception = assertThrows(CompletionException.class, () -> outboxRelay.relay());

        assertSame(failure, exception.getCause());
        assertEquals(OutboxStatus.PENDING, message.getStatus());
        verify(outboxService, never()).markPublished(any(UUID.class), any(Instant.class));
    }

    @Test
    void relay_shouldDoNothingWhenNoPendingMessagesExist() {
        when(outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of());

        outboxRelay.relay();

        verifyNoInteractions(messagePublisher, outboxService);
    }

    @Test
    void relay_shouldPublishAndMarkAllPendingMessages() {
        UUID firstAggregateId = UUID.fromString("80000000-0000-0000-0000-000000000001");
        UUID secondAggregateId = UUID.fromString("80000000-0000-0000-0000-000000000002");
        OutboxMessage firstMessage = pendingMessage(firstAggregateId, CREATED_AT, PAYLOAD);
        OutboxMessage secondMessage = pendingMessage(
                secondAggregateId,
                CREATED_AT.plusSeconds(1),
                PAYLOAD
        );
        CompletableFuture<SendResult<String, MessageEnvelope>> sendFuture = CompletableFuture.completedFuture(null);
        ArgumentCaptor<UUID> keyCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<MessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(MessageEnvelope.class);

        when(outboxMessageRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING))
                .thenReturn(List.of(firstMessage, secondMessage));
        when(messagePublisher.publish(eq(TOPIC), keyCaptor.capture(), envelopeCaptor.capture()))
                .thenReturn(sendFuture);

        outboxRelay.relay();

        verify(messagePublisher, times(2))
                .publish(eq(TOPIC), any(UUID.class), any(MessageEnvelope.class));
        assertEquals(List.of(firstAggregateId, secondAggregateId), keyCaptor.getAllValues());
        assertEquals(
                List.of(firstMessage.getId(), secondMessage.getId()),
                envelopeCaptor.getAllValues().stream().map(MessageEnvelope::messageId).toList()
        );
        verify(outboxService).markPublished(eq(firstMessage.getId()), any(Instant.class));
        verify(outboxService).markPublished(eq(secondMessage.getId()), any(Instant.class));
    }

    private static OutboxMessage pendingMessage(UUID aggregateId, Instant createdAt, JsonNode payload) {
        return OutboxMessage.create(
                aggregateId,
                OutboxMessageType.ORDER_CHECKOUT_STARTED,
                payload,
                createdAt
        );
    }
}
