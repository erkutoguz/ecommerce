package dev.erkut.authservice.outbox.application;

import dev.erkut.authservice.message.command.CreateCustomerCommand;
import dev.erkut.authservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.authservice.outbox.domain.OutboxMessage;
import dev.erkut.authservice.outbox.domain.OutboxMessageType;
import dev.erkut.authservice.outbox.domain.OutboxStatus;
import dev.erkut.authservice.outbox.persistence.OutboxMessageRepository;
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
    public void createCustomerCommand(CreateCustomerCommand command, Instant createdAt) {
        if (command == null) {
            throw new IllegalArgumentException("Create customer command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.authUserId(),
                OutboxMessageType.CREATE_CUSTOMER_COMMAND,
                payload,
                createdAt
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
