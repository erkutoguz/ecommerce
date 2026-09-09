package dev.erkut.orderworkflowservice.outbox.application;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.orderworkflowservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

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

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
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
        relay = new OutboxRelay(messagePublisher, topicResolver, outboxService);
    }

    @Test
    void relay_shouldDoNothingWhenNoPendingMessagesExist() {
        when(outboxService.findPendingMessages()).thenReturn(List.of());

        relay.relay();

        verify(messagePublisher, never()).publish(any(), any(), any());
        verify(outboxService, never()).markPublished(any(), any());
    }

    @ParameterizedTest
    @MethodSource("commandTopics")
    void relay_shouldRouteCommandWithAggregateKeyAndMarkPublishedAfterBrokerAck(
            OutboxMessageType messageType,
            String topic
    ) {
        JsonMapper mapper = new JsonMapper();
        var payload = mapper.createObjectNode().put("orderId", ORDER_ID.toString());
        OutboxMessage message = OutboxMessage.create(
                ORDER_ID,
                messageType,
                payload,
                CREATED_AT
        );
        CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(null);
        ArgumentCaptor<MessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(MessageEnvelope.class);
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(topicResolver.resolve(messageType)).thenReturn(topic);
        when(messagePublisher.publish(eq(topic), eq(ORDER_ID), envelopeCaptor.capture()))
                .thenReturn(future);

        relay.relay();

        MessageEnvelope envelope = envelopeCaptor.getValue();
        assertEquals(message.getId(), envelope.messageId());
        assertEquals(messageType.name(), envelope.messageType());
        assertEquals(CREATED_AT, envelope.occurredAt());
        assertSame(payload, envelope.payload());
        verify(messagePublisher).publish(eq(topic), eq(ORDER_ID), any(MessageEnvelope.class));
        verify(outboxService).markPublished(eq(message.getId()), any(Instant.class));
    }

    @Test
    void relay_shouldLeaveMessagePendingWhenPublishFails() {
        OutboxMessage message = OutboxMessage.create(
                ORDER_ID,
                OutboxMessageType.RESERVE_STOCK_COMMAND,
                new JsonMapper().createObjectNode(),
                CREATED_AT
        );
        CompletableFuture<SendResult<String, Object>> future = new CompletableFuture<>();
        RuntimeException failure = new RuntimeException("Kafka unavailable");
        future.completeExceptionally(failure);
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(topicResolver.resolve(OutboxMessageType.RESERVE_STOCK_COMMAND)).thenReturn("stock.commands");
        when(messagePublisher.publish(eq("stock.commands"), eq(ORDER_ID), any(MessageEnvelope.class)))
                .thenReturn(future);

        assertThrows(java.util.concurrent.CompletionException.class, () -> relay.relay());

        verify(outboxService, never()).markPublished(any(), any());
    }

    private static Stream<Arguments> commandTopics() {
        return Stream.of(
                Arguments.of(OutboxMessageType.RESERVE_STOCK_COMMAND, "stock.commands"),
                Arguments.of(OutboxMessageType.REJECT_ORDER_COMMAND, "order.commands"),
                Arguments.of(OutboxMessageType.INITIATE_PAYMENT_COMMAND, "payment.commands")
        );
    }
}
