package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.orderservice.messaging.kafka.routing.KafkaTopicResolver;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
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
            KafkaTopicResolver kafkaTopicResolver1
    ) {
        this.outboxService = outboxService;
        this.messagePublisher = messagePublisher;
        this.kafkaTopicResolver = kafkaTopicResolver1;
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
