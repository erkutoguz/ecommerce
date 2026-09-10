package dev.erkut.paymentservice.outbox.application;

import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.paymentservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.paymentservice.outbox.domain.OutboxMessage;
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
        messages.forEach(this::publish);
    }

    private void publish(OutboxMessage message) {
        MessageEnvelope envelope = new MessageEnvelope(
                message.getId(),
                message.getMessageType().name(),
                message.getCreatedAt(),
                message.getPayload()
        );

        messagePublisher.publish(
                kafkaTopicResolver.resolve(message.getMessageType()),
                message.getAggregateId(),
                envelope
        ).join();

        outboxService.markPublished(message.getId(), Instant.now());
    }
}
