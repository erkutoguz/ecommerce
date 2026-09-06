package dev.erkut.orderworkflowservice.outbox.application;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.orderworkflowservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
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
    public OutboxRelay(
            KafkaMessagePublisher messagePublisher,
            KafkaTopicResolver kafkaTopicResolver,
            OutboxService outboxService
    ) {
        this.messagePublisher = messagePublisher;
        this.kafkaTopicResolver = kafkaTopicResolver;
        this.outboxService = outboxService;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
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
