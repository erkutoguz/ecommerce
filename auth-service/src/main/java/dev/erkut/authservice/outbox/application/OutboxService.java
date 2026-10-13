package dev.erkut.authservice.outbox.application;

import dev.erkut.authservice.message.command.CreateCustomerCommand;
import dev.erkut.authservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.authservice.outbox.domain.OutboxMessage;
import dev.erkut.authservice.outbox.domain.OutboxMessageType;
import dev.erkut.authservice.outbox.domain.OutboxStatus;
import dev.erkut.authservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.authservice.observability.tracing.OutboxTraceContext;
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
    public void createCustomerCommand(CreateCustomerCommand command, Instant createdAt) {
        if (command == null) {
            throw new IllegalArgumentException("Create customer command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxTraceContext.Headers traceContext = outboxTraceContext.capture();
        OutboxMessage message = OutboxMessage.create(
                command.authUserId(),
                OutboxMessageType.CREATE_CUSTOMER_COMMAND,
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
    public boolean markPublished(UUID messageId, UUID claimToken, Instant publishedAt) {
        return outboxRepository.markClaimedMessagePublished(messageId, claimToken, publishedAt) == 1;
    }

    @Transactional
    public boolean reschedule(UUID messageId, UUID claimToken, Instant retryAt) {
        return outboxRepository.rescheduleClaimedMessage(messageId, claimToken, retryAt) == 1;
    }


    private JsonNode serialize(Object command) {
        try {
            return jsonMapper.valueToTree(command);
        } catch (JacksonException exception) {
            throw new OutboxSerializationException("Command could not be serialized", exception);
        }
    }


}
