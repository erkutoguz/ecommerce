package dev.erkut.productservice.outbox.application;

import dev.erkut.productservice.message.MessageEnvelope;
import dev.erkut.productservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.productservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.productservice.outbox.domain.OutboxMessage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@ConditionalOnProperty(name = "outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private final OutboxService outboxService;
    private final KafkaMessagePublisher messagePublisher;
    private final KafkaTopicResolver topicResolver;
    public OutboxRelay(
            OutboxService outboxService,
            KafkaMessagePublisher messagePublisher,
            KafkaTopicResolver topicResolver
    ) {
        this.outboxService = outboxService;
        this.messagePublisher = messagePublisher;
        this.topicResolver = topicResolver;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-relay-ms}")
    public void relay() {
        List<OutboxMessage> messages = outboxService.findPendingMessages();

        for(OutboxMessage message : messages) {
            publish(message);
        }
    }

    private void publish(OutboxMessage message) {
        MessageEnvelope envelope = new MessageEnvelope(
          message.getId(),
          message.getMessageType().name(),
          message.getCreatedAt(),
          message.getPayload()
        );

        String topic =
                topicResolver.resolve(message.getMessageType());

        messagePublisher.publish(
                topic,
                message.getAggregateId(),
                envelope
        ).join();

        outboxService.markPublished(message.getId(), Instant.now());

    }
}
