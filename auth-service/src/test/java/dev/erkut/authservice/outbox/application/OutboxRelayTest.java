package dev.erkut.authservice.outbox.application;

import dev.erkut.authservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.authservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.authservice.outbox.domain.OutboxMessage;
import dev.erkut.authservice.outbox.domain.OutboxMessageType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private OutboxService outboxService;

    @Mock
    private KafkaMessagePublisher messagePublisher;

    @Mock
    private KafkaTopicResolver topicResolver;

    @Test
    void successfulKafkaPublishMarksMessagePublished() {
        OutboxMessage message = message();
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(topicResolver.resolve(OutboxMessageType.CREATE_CUSTOMER_COMMAND)).thenReturn("customer.commands");
        when(messagePublisher.publish(eq("customer.commands"), eq(AUTH_USER_ID), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OutboxRelay(outboxService, messagePublisher, topicResolver).relay();

        verify(outboxService).markPublished(eq(message.getId()), any(Instant.class));
    }

    @Test
    void failedKafkaPublishLeavesMessagePending() {
        OutboxMessage message = message();
        when(outboxService.findPendingMessages()).thenReturn(List.of(message));
        when(topicResolver.resolve(OutboxMessageType.CREATE_CUSTOMER_COMMAND)).thenReturn("customer.commands");
        when(messagePublisher.publish(eq("customer.commands"), eq(AUTH_USER_ID), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka unavailable")));

        assertThrows(CompletionException.class,
                () -> new OutboxRelay(outboxService, messagePublisher, topicResolver).relay());

        verify(outboxService, never()).markPublished(any(), any());
    }

    private static OutboxMessage message() {
        return OutboxMessage.create(
                AUTH_USER_ID,
                OutboxMessageType.CREATE_CUSTOMER_COMMAND,
                new JsonMapper().createObjectNode().put("authUserId", AUTH_USER_ID.toString()),
                CREATED_AT
        );
    }
}
