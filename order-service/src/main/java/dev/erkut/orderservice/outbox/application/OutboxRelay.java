package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.orderservice.messaging.kafka.producer.KafkaMessagePublisher;
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
    private final KafkaMessagePublisher publisher;
    private final KafkaTopicsProperties topics;

    public OutboxRelay(
            OutboxService outboxService,
            KafkaMessagePublisher publisher,
            KafkaTopicsProperties topics
    ) {
        this.outboxService = outboxService;
        this.publisher = publisher;
        this.topics = topics;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    public void relayPendingMessages() {

        List<OutboxMessage> messages =
                outboxService.findPendingMessages();

        for (OutboxMessage message : messages) {

            MessageEnvelope envelope =
                    new MessageEnvelope(
                            message.getId(),
                            message.getMessageType().name(),
                            message.getCreatedAt(),
                            message.getPayload()
                    );

            publisher.publish(
                    topics.orderEvents(),
                    message.getAggregateId(),
                    envelope
            ).join();

            outboxService.markPublished(
                    message.getId(),
                    Instant.now()
            );
        }
    }

    /**
     * Kept as a small compatibility entry point for existing callers/tests.
     */
    public void relay() {
        relayPendingMessages();
    }


}
