package dev.erkut.paymentservice.outbox.application;

import dev.erkut.paymentservice.message.event.PaymentCompletedEvent;
import dev.erkut.paymentservice.message.event.PaymentFailedEvent;
import dev.erkut.paymentservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.paymentservice.outbox.domain.OutboxMessage;
import dev.erkut.paymentservice.outbox.domain.OutboxMessageType;
import dev.erkut.paymentservice.outbox.domain.OutboxStatus;
import dev.erkut.paymentservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.paymentservice.observability.tracing.OutboxTraceContext;
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
    public void handlePaymentCompletedEvent(PaymentCompletedEvent event, Instant createdAt) {
        if (event == null) {
            throw new IllegalArgumentException("Payment event cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        JsonNode payload = serialize(event);
        OutboxTraceContext.Headers headers = outboxTraceContext.capture();
        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.PAYMENT_COMPLETED_EVENT,
                payload,
                createdAt,
                headers == null ? null : headers.traceparent(),
                headers == null ? null : headers.tracestate()
        );
        outboxRepository.save(message);
    }

    @Transactional
    public void handlePaymentFailedEvent(PaymentFailedEvent event, Instant createdAt) {
        if (event == null) {
            throw new IllegalArgumentException("Payment event cannot be null");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("Creation time cannot be null");
        }

        JsonNode payload = serialize(event);
        OutboxTraceContext.Headers headers = outboxTraceContext.capture();
        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.PAYMENT_FAILED_EVENT,
                payload,
                createdAt,
                headers == null ? null : headers.traceparent(),
                headers == null ? null : headers.tracestate()
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

    private JsonNode serialize(Object command) {
        try {
            return jsonMapper.valueToTree(command);
        } catch (JacksonException exception) {
            throw new OutboxSerializationException("Command could not be serialized", exception);
        }
    }
}
