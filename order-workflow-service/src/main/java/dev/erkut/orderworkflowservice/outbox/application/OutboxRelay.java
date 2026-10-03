package dev.erkut.orderworkflowservice.outbox.application;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.orderworkflowservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.observability.tracing.OutboxTraceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final KafkaMessagePublisher messagePublisher;
    private final KafkaTopicResolver kafkaTopicResolver;
    private final OutboxService outboxService;
    private final OutboxTraceContext outboxTraceContext;
    public OutboxRelay(
            KafkaMessagePublisher messagePublisher,
            KafkaTopicResolver kafkaTopicResolver,
            OutboxService outboxService,
            OutboxTraceContext outboxTraceContext
    ) {
        this.messagePublisher = messagePublisher;
        this.kafkaTopicResolver = kafkaTopicResolver;
        this.outboxService = outboxService;
        this.outboxTraceContext = outboxTraceContext;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    public void relay() {
        List<OutboxMessage> messages = outboxService.findPendingMessages();

        for(OutboxMessage message : messages) {
            publish(message);
        }
    }

    private void publish(OutboxMessage message) {
        OutboxTraceContext.Headers headers = message.getTraceparent() == null
                ? null
                : new OutboxTraceContext.Headers(message.getTraceparent(), message.getTracestate());
        outboxTraceContext.runWithParent(headers, () -> publishWithCurrentContext(message));
    }

    private void publishWithCurrentContext(OutboxMessage message) {
        MessageEnvelope envelope = new MessageEnvelope(
          message.getId(),
          message.getMessageType().name(),
          message.getCreatedAt(),
          message.getPayload()
        );

        String topic =
                kafkaTopicResolver.resolve(message.getMessageType());

        messagePublisher.publish(
                topic,
                message.getAggregateId(),
                envelope
        ).join();

        outboxService.markPublished(
                message.getId(),
                Instant.now()
        );
    }

}
