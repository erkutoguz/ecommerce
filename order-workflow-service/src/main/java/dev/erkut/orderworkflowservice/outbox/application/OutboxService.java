package dev.erkut.orderworkflowservice.outbox.application;

import dev.erkut.orderworkflowservice.message.command.*;
import dev.erkut.orderworkflowservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxStatus;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
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
    public void createReserveStockCommand(ReserveStockCommand command, Instant createdAt) {
        if (command == null) {
            throw new IllegalArgumentException("Reserve stock command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.orderId(),
                OutboxMessageType.RESERVE_STOCK_COMMAND,
                payload,
                createdAt
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createRejectOrderCommand(RejectOrderCommand command, Instant createdAt) {
        if (command == null) {
            throw new IllegalArgumentException("Reject order command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.orderId(),
                OutboxMessageType.REJECT_ORDER_COMMAND,
                payload,
                createdAt
        );
        outboxRepository.save(message);
    }

    @Transactional
    public void handleInitiatePaymentCommand(InitiatePaymentCommand command, Instant createdAt) {
        if(command == null) {
            throw new IllegalArgumentException("Initiate payment command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.orderId(),
                OutboxMessageType.INITIATE_PAYMENT_COMMAND,
                payload,
                createdAt
        );
        outboxRepository.save(message);
    }

    @Transactional
    public void createMarkOrderStockReservedCommand(
            MarkOrderStockReservedCommand command,
            Instant createdAt
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Mark order stock reserved command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.orderId(),
                OutboxMessageType.MARK_ORDER_STOCK_RESERVED_COMMAND,
                payload,
                createdAt
        );
        outboxRepository.save(message);
    }

    @Transactional
    public void createMarkOrderPaymentCompletedCommand(
            MarkOrderPaymentCompletedCommand command,
            Instant createdAt
    ) {
        if (command == null) {
            throw new IllegalArgumentException("Mark order payment completed command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.orderId(),
                OutboxMessageType.MARK_ORDER_PAYMENT_COMPLETED_COMMAND,
                payload,
                createdAt
        );
        outboxRepository.save(message);
    }

    @Transactional
    public void handleConfirmStockReservationCommand(ConfirmStockReservationCommand command, Instant createdAt) {
        if(command == null) {
            throw new IllegalArgumentException("Confirm stock command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.orderId(),
                OutboxMessageType.CONFIRM_STOCK_RESERVATION_COMMAND,
                payload,
                createdAt
        );
        outboxRepository.save(message);
    }

    @Transactional
    public void handleConfirmOrderCommand(ConfirmOrderCommand command, Instant createdAt) {
        if(command == null) {
            throw new IllegalArgumentException("Confirm order command cannot be null");
        }

        JsonNode payload = serialize(command);
        OutboxMessage message = OutboxMessage.create(
                command.orderId(),
                OutboxMessageType.CONFIRM_ORDER_COMMAND,
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
