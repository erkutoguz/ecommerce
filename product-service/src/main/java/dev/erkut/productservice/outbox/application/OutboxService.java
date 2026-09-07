package dev.erkut.productservice.outbox.application;

import dev.erkut.productservice.message.event.ProductCreatedEvent;
import dev.erkut.productservice.message.event.ProductDeactivatedEvent;
import dev.erkut.productservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.productservice.outbox.domain.OutboxMessage;
import dev.erkut.productservice.outbox.domain.OutboxMessageType;
import dev.erkut.productservice.outbox.domain.OutboxStatus;
import dev.erkut.productservice.outbox.persistence.OutboxMessageRepository;
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
    public OutboxService(OutboxMessageRepository outboxRepository, JsonMapper jsonMapper) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void createProductCreatedEvent(ProductCreatedEvent event, Instant createdAt) {
        if(event == null) {
            throw new IllegalArgumentException("Product created event cannot be null");
        }

        JsonNode payload = serialize(event);
        OutboxMessage message = OutboxMessage.create(
                event.productId(),
                OutboxMessageType.PRODUCT_CREATED_EVENT,
                payload,
                createdAt
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createProductDeactivatedEvent(ProductDeactivatedEvent event, Instant deactivatedAt) {
        if(event == null) {
            throw new IllegalArgumentException("Product created event cannot be null");
        }

        JsonNode payload = serialize(event);
        OutboxMessage message = OutboxMessage.create(
                event.productId(),
                OutboxMessageType.PRODUCT_DEACTIVATED_EVENT,
                payload,
                deactivatedAt
        );

        outboxRepository.save(message);

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

    public JsonNode serialize(Object event) {
        try {
            return jsonMapper.valueToTree(event);
        } catch (JacksonException exception) {
            throw new OutboxSerializationException("Event object could not be serialized", exception);
        }
    }
}
