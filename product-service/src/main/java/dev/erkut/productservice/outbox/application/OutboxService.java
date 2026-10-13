package dev.erkut.productservice.outbox.application;

import dev.erkut.productservice.message.event.ProductCreatedEvent;
import dev.erkut.productservice.message.event.ProductDeactivatedEvent;
import dev.erkut.productservice.message.event.ProductDeletedEvent;
import dev.erkut.productservice.message.event.ProductUpdatedEvent;
import dev.erkut.productservice.observability.tracing.OutboxTraceContext;
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
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

@Service
public class OutboxService {

    private final OutboxMessageRepository outboxRepository;
    private final JsonMapper jsonMapper;
    private final OutboxTraceContext outboxTraceContext;
    public OutboxService(
            OutboxMessageRepository outboxRepository,
            JsonMapper jsonMapper,
            OutboxTraceContext outboxTraceContext
    ) {
        this.outboxRepository = outboxRepository;
        this.jsonMapper = jsonMapper;
        this.outboxTraceContext = outboxTraceContext;
    }

    @Transactional
    public void createProductCreatedEvent(ProductCreatedEvent event, Instant createdAt) {
        saveProductEvent(event == null ? null : event.productId(),
                OutboxMessageType.PRODUCT_CREATED_EVENT, event, createdAt);
    }

    @Transactional
    public void createProductUpdatedEvent(ProductUpdatedEvent event, Instant updatedAt) {
        saveProductEvent(event == null ? null : event.productId(),
                OutboxMessageType.PRODUCT_UPDATED_EVENT, event, updatedAt);
    }

    @Transactional
    public void createProductDeactivatedEvent(ProductDeactivatedEvent event, Instant deactivatedAt) {
        saveProductEvent(event == null ? null : event.productId(),
                OutboxMessageType.PRODUCT_DEACTIVATED_EVENT, event, deactivatedAt);
    }

    @Transactional
    public void createProductDeletedEvent(ProductDeletedEvent event, Instant deletedAt) {
        saveProductEvent(event == null ? null : event.productId(),
                OutboxMessageType.PRODUCT_DELETED_EVENT, event, deletedAt);
    }

    private void saveProductEvent(
            UUID productId,
            OutboxMessageType messageType,
            Object event,
            Instant createdAt
    ) {
        if (event == null) {
            throw new IllegalArgumentException("Product event cannot be null");
        }

        JsonNode payload = serialize(event);
        OutboxTraceContext.Headers traceContext = outboxTraceContext.capture();
        OutboxMessage message = OutboxMessage.create(
                productId,
                messageType,
                payload,
                createdAt,
                traceContext == null ? null : traceContext.traceparent(),
                traceContext == null ? null : traceContext.tracestate()
        );
        outboxRepository.save(message);
    }

    @Transactional
    public List<ClaimedOutboxMessage> claimMessages(int batchSize, long leaseSeconds) {
        List<Object[]> claims = outboxRepository.claimEligibleMessages(batchSize, leaseSeconds);
        if (claims.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> tokenById = new HashMap<>();
        for (Object[] claim : claims) {
            tokenById.put((UUID) claim[0], (UUID) claim[1]);
        }
        return outboxRepository.findAllById(tokenById.keySet()).stream()
                .map(message -> new ClaimedOutboxMessage(message, tokenById.get(message.getId())))
                .sorted(java.util.Comparator.comparing((ClaimedOutboxMessage claim) -> claim.message().getCreatedAt())
                        .thenComparing(claim -> claim.message().getId()))
                .toList();
    }

    @Transactional
    public boolean markPublished(UUID messageId, UUID claimToken, Instant publishedAt) {
        return outboxRepository.markClaimedMessagePublished(messageId, claimToken, publishedAt) == 1;
    }

    @Transactional
    public boolean reschedule(UUID messageId, UUID claimToken, Instant retryAt) {
        return outboxRepository.rescheduleClaimedMessage(messageId, claimToken, retryAt) == 1;
    }

    public JsonNode serialize(Object event) {
        try {
            return jsonMapper.valueToTree(event);
        } catch (JacksonException exception) {
            throw new OutboxSerializationException("Event object could not be serialized", exception);
        }
    }
}
