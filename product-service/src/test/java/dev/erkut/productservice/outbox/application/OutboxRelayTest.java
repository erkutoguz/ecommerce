package dev.erkut.productservice.outbox.application;

import dev.erkut.productservice.message.MessageEnvelope;
import dev.erkut.productservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.productservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.productservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.productservice.outbox.domain.OutboxMessage;
import dev.erkut.productservice.outbox.domain.OutboxMessageType;
import dev.erkut.productservice.outbox.domain.OutboxStatus;
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

    private static final String TOPIC = "product.events";
    private static final UUID PRODUCT_ID =
            UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaMessagePublisher publisher;

    @Test
    void relayMapsOutboxToEnvelopePublishesWithProductKeyAndMarksAfterAck() {
        var payload = new JsonMapper().readTree("{\"productId\":\"" + PRODUCT_ID + "\"}");
        OutboxMessage message = OutboxMessage.create(
                PRODUCT_ID, OutboxMessageType.PRODUCT_CREATED_EVENT, payload, CREATED_AT);
        var future = CompletableFuture.<SendResult<String, MessageEnvelope>>completedFuture(null);
        ArgumentCaptor<MessageEnvelope> envelopeCaptor =
                ArgumentCaptor.forClass(MessageEnvelope.class);

        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(publisher.publish(eq(TOPIC), eq(PRODUCT_ID), envelopeCaptor.capture()))
                .thenReturn(future);

        relay().relay();

        MessageEnvelope envelope = envelopeCaptor.getValue();
        assertEquals(message.getId(), envelope.messageId());
        assertEquals("PRODUCT_CREATED_EVENT", envelope.messageType());
        assertEquals(CREATED_AT, envelope.occurredAt());
        assertSame(payload, envelope.payload());
        verify(outboxService).markPublished(eq(message.getId()), any(Instant.class));
    }

    @Test
    void relayLeavesMessagePendingWhenKafkaFails() {
        OutboxMessage message = OutboxMessage.create(
                PRODUCT_ID,
                OutboxMessageType.PRODUCT_CREATED_EVENT,
                new JsonMapper().readTree("{\"productId\":\"" + PRODUCT_ID + "\"}"),
                CREATED_AT);
        var failure = new RuntimeException("Kafka unavailable");
        var future = new CompletableFuture<SendResult<String, MessageEnvelope>>();
        future.completeExceptionally(failure);

        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(publisher.publish(eq(TOPIC), eq(PRODUCT_ID), any(MessageEnvelope.class)))
                .thenReturn(future);

        assertThrows(java.util.concurrent.CompletionException.class, () -> relay().relay());

        assertEquals(OutboxStatus.PENDING, message.getStatus());
        verify(outboxService, never()).markPublished(any(UUID.class), any(Instant.class));
    }

    private OutboxRelay relay() {
        return new OutboxRelay(
                outboxService,
                publisher,
                new KafkaTopicResolver(new KafkaTopicsProperties(TOPIC))
        );
    }
}
