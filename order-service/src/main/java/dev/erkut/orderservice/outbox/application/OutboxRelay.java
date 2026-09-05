package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.messaging.kafka.producer.KafkaMessagePublisher;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class OutboxRelay {

    private final OutboxMessageRepository outboxMessageRepository;
    private final OutboxService outboxService;
    private final KafkaMessagePublisher messagePublisher;
    private final String orderEventsTopic;
    public OutboxRelay(
            OutboxMessageRepository outboxMessageRepository,
            OutboxService outboxService,
            KafkaMessagePublisher messagePublisher,
            @Value("${kafka.order.topic}") String orderEventsTopic
    ) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.outboxService = outboxService;
        this.messagePublisher = messagePublisher;
        this.orderEventsTopic = orderEventsTopic;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:2000}")
    public void relay() {
        List<OutboxMessage> messages = outboxMessageRepository
                .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

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

        messagePublisher.publish(
                orderEventsTopic,
                message.getAggregateId(),
                envelope
        ).join();

        outboxService.markPublished(
                message.getId(),
                Instant.now()
        );
    }
}


