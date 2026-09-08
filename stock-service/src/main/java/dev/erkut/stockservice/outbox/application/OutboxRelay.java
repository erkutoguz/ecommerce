package dev.erkut.stockservice.outbox.application;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.stockservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.stockservice.outbox.domain.OutboxMessage;
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
    private final KafkaTopicResolver kafkaTopicResolver;
    public OutboxRelay(
            OutboxService outboxService,
            KafkaMessagePublisher messagePublisher,
            KafkaTopicResolver kafkaTopicResolver
    ) {
        this.outboxService = outboxService;
        this.messagePublisher = messagePublisher;
        this.kafkaTopicResolver = kafkaTopicResolver;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms}")
    public void relay() {
        List<OutboxMessage> outboxMessages = outboxService.findPendingMessages();
        for(OutboxMessage message : outboxMessages) {
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
