package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderservice.message.event.OrderConfirmedEvent;
import dev.erkut.orderservice.message.event.OrderRejectedEvent;
import dev.erkut.orderservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.domain.exception.InvalidOutboxMessageException;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.orderservice.observability.tracing.OutboxTraceContext;
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
    public List<ClaimedOutboxMessage> claimMessages(int batchSize, long leaseSeconds) {
        List<Object[]> claims = outboxRepository.claimEligibleMessages(batchSize, leaseSeconds);
        if (claims.isEmpty()) return List.of();
        java.util.Map<UUID, UUID> tokenById = new java.util.HashMap<>();
        for (Object[] row : claims) tokenById.put((UUID) row[0], (UUID) row[1]);
        return outboxRepository.findAllById(tokenById.keySet()).stream()
                .map(message -> new ClaimedOutboxMessage(message, tokenById.get(message.getId())))
                .sorted(java.util.Comparator.comparing((ClaimedOutboxMessage c) -> c.message().getCreatedAt())
                        .thenComparing(c -> c.message().getId()))
                .toList();
    }

    @Transactional
    public void createOrderCheckoutStartedMessage(OrderCheckoutStartedEvent event, Instant createdAt) {
        if (event == null) {
            throw new InvalidOutboxMessageException("Event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = createMessage(
                event.orderId(),
                OutboxMessageType.ORDER_CHECKOUT_STARTED,
                payload,
                createdAt,
                outboxTraceContext.capture()
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createOrderRejectedEvent(OrderRejectedEvent event, Instant createdAt) {
        if (event == null) {
            throw new InvalidOutboxMessageException("Event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = createMessage(
                event.orderId(),
                OutboxMessageType.ORDER_REJECTED_EVENT,
                payload,
                createdAt,
                outboxTraceContext.capture()
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createOrderConfirmedEvent(OrderConfirmedEvent event, Instant createdAt) {
        if (event == null) {
            throw new InvalidOutboxMessageException("Event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = createMessage(
                event.orderId(),
                OutboxMessageType.ORDER_CONFIRMED_EVENT,
                payload,
                createdAt,
                outboxTraceContext.capture()
        );

        outboxRepository.save(message);
    }

    @Transactional
    public boolean markPublished(UUID messageId, UUID claimToken, Instant publishedAt) {
        return outboxRepository.markClaimedMessagePublished(messageId, claimToken, publishedAt) == 1;
    }

    @Transactional
    public boolean reschedule(UUID messageId, UUID claimToken, Instant retryAt) {
        return outboxRepository.rescheduleClaimedMessage(messageId, claimToken, retryAt) == 1;
    }

    private JsonNode serialize(Object event) {
        try {
            return jsonMapper.valueToTree(event);
        } catch (JacksonException ex) {
            throw new OutboxSerializationException("Event couldn't serialized", ex);
        }
    }

    private OutboxMessage createMessage(
            UUID aggregateId,
            OutboxMessageType messageType,
            JsonNode payload,
            Instant createdAt,
            OutboxTraceContext.Headers traceContext
    ) {
        return OutboxMessage.create(
                aggregateId,
                messageType,
                payload,
                createdAt,
                traceContext == null ? null : traceContext.traceparent(),
                traceContext == null ? null : traceContext.tracestate()
        );
    }
}
