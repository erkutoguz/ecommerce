package dev.erkut.paymentservice.outbox.application;

import dev.erkut.paymentservice.message.event.PaymentCompletedEvent;
import dev.erkut.paymentservice.message.event.PaymentFailedEvent;
import dev.erkut.paymentservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.paymentservice.outbox.domain.OutboxMessage;
import dev.erkut.paymentservice.outbox.domain.OutboxStatus;
import dev.erkut.paymentservice.outbox.persistence.OutboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxMessageRepository outboxRepository;
    private final JsonMapper jsonMapper;

    public OutboxService(
            OutboxMessageRepository outboxRepository,
            JsonMapper jsonMapper
    ) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void handlePaymentCompletedEvent(PaymentCompletedEvent event, Instant createdAt) {
        if(event == null) {
            throw new IllegalArgumentException("Payment event cannot be null");
        }

        JsonNode payload = serialize(event);


    }

    @Transactional
    public void handlePaymentFailedEvent(PaymentFailedEvent event, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<OutboxMessage> findPendingMessages() {
        return outboxRepository.findTop100ByStatusOrderByCreatedAtAsc(
                OutboxStatus.PENDING
        );
    }

    @Transactional
    public void markPublished(UUID messageId, Instant publishedAt) {
        OutboxMessage message = outboxRepository.findById(messageId)
                .orElseThrow(() ->
                        new IllegalStateException("Outbox message not found: " + messageId)
                );

        message.markPublished(publishedAt);
    }

    private JsonNode serialize(Object command) {
        try {
            return jsonMapper.valueToTree(command);
        } catch (JacksonException exception) {
            throw new OutboxSerializationException("Command could not be serialized", exception);
        }
    }
}
