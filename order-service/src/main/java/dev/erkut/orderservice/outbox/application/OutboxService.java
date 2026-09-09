package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderservice.message.event.OrderRejectedEvent;
import dev.erkut.orderservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.domain.exception.InvalidOutboxMessageException;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
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

    @Transactional(readOnly = true)
    public List<OutboxMessage> findPendingMessages() {
        return outboxRepository
                .findTop100ByStatusOrderByCreatedAtAsc(
                        OutboxStatus.PENDING
                );
    }

    @Transactional
    public void createOrderCheckoutStartedMessage(OrderCheckoutStartedEvent event, Instant createdAt) {
        if (event == null) {
            throw new InvalidOutboxMessageException("Event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.ORDER_CHECKOUT_STARTED,
                payload,
                createdAt
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createOrderRejectedEvent(OrderRejectedEvent event, Instant createdAt) {
        if (event == null) {
            throw new InvalidOutboxMessageException("Event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.ORDER_REJECTED_EVENT,
                payload,
                createdAt
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void markPublished(UUID messageId, Instant publishedAt) {
        OutboxMessage message = outboxRepository.findById(messageId)
                .orElseThrow(() ->
                        new IllegalStateException("Outbox message not found: " + messageId)
                );

        message.markPublished(publishedAt);
    }

    private JsonNode serialize(Object event) {
        try {
            return jsonMapper.valueToTree(event);
        } catch (JacksonException ex) {
            throw new OutboxSerializationException("Event couldn't serialized", ex);
        }
    }
}
