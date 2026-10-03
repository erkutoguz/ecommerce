package dev.erkut.paymentservice.outbox.application;

import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.paymentservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.paymentservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.paymentservice.observability.tracing.OutboxTraceContext;
import dev.erkut.paymentservice.outbox.domain.OutboxMessage;
import dev.erkut.paymentservice.outbox.domain.OutboxMessageType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000301");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private KafkaMessagePublisher messagePublisher;

    @Mock
    private OutboxService outboxService;

    @Mock
    private OutboxTraceContext outboxTraceContext;

    @Test
    void relay_shouldPublishPaymentCompletedEventAndMarkItPublished() {
        JsonMapper jsonMapper = new JsonMapper();
        OutboxTraceContext.Headers traceContext = new OutboxTraceContext.Headers(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "vendor=value"
        );
        OutboxMessage message = OutboxMessage.create(
                ORDER_ID,
                OutboxMessageType.PAYMENT_COMPLETED_EVENT,
                jsonMapper.createObjectNode().put("orderId", ORDER_ID.toString()),
                CREATED_AT,
                traceContext.traceparent(),
                traceContext.tracestate()
        );
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(messagePublisher.publish(
                eq("payment.events"),
                eq(ORDER_ID),
                any(MessageEnvelope.class)
        )).thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));

        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(outboxTraceContext).runWithParent(any(), any());
        OutboxRelay relay = new OutboxRelay(
                messagePublisher,
                new KafkaTopicResolver(new KafkaTopicsProperties(
                        "payment.commands",
                        "payment.events",
                        "payment.commands.DLT"
                )),
                outboxService,
                outboxTraceContext
        );

        relay.relay();

        ArgumentCaptor<MessageEnvelope> envelopeCaptor = ArgumentCaptor.forClass(MessageEnvelope.class);
        verify(messagePublisher).publish(eq("payment.events"), eq(ORDER_ID), envelopeCaptor.capture());
        MessageEnvelope envelope = envelopeCaptor.getValue();
        assertEquals(message.getId(), envelope.messageId());
        assertEquals("PAYMENT_COMPLETED_EVENT", envelope.messageType());
        assertEquals(CREATED_AT, envelope.occurredAt());
        assertEquals(message.getPayload(), envelope.payload());
        verify(outboxService).markPublished(eq(message.getId()), any(Instant.class));
        verify(outboxTraceContext).runWithParent(eq(traceContext), any());
    }

    @Test
    void relay_shouldPublishPaymentFailedEventToPaymentEventsTopic() {
        JsonMapper jsonMapper = new JsonMapper();
        OutboxMessage message = OutboxMessage.create(
                ORDER_ID,
                OutboxMessageType.PAYMENT_FAILED_EVENT,
                jsonMapper.createObjectNode()
                        .put("orderId", ORDER_ID.toString())
                        .put("failureReason", "SESSION_EXPIRED"),
                CREATED_AT
        );
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(messagePublisher.publish(
                eq("payment.events"),
                eq(ORDER_ID),
                any(MessageEnvelope.class)
        )).thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));

        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(1).run();
            return null;
        }).when(outboxTraceContext).runWithParent(any(), any());
        OutboxRelay relay = new OutboxRelay(
                messagePublisher,
                new KafkaTopicResolver(new KafkaTopicsProperties(
                        "payment.commands",
                        "payment.events",
                        "payment.commands.DLT"
                )),
                outboxService,
                outboxTraceContext
        );

        relay.relay();

        verify(messagePublisher).publish(eq("payment.events"), eq(ORDER_ID), any(MessageEnvelope.class));
        verify(outboxService).markPublished(eq(message.getId()), any(Instant.class));
    }
}
