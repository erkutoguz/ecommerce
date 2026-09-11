package dev.erkut.stockservice.outbox.application;

import dev.erkut.stockservice.message.event.StockReservationConfirmedEvent;
import dev.erkut.stockservice.message.event.StockReservationFailedEvent;
import dev.erkut.stockservice.message.event.StockReservationReleasedEvent;
import dev.erkut.stockservice.message.event.StockReservedEvent;
import dev.erkut.stockservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import dev.erkut.stockservice.outbox.domain.OutboxMessageType;
import dev.erkut.stockservice.outbox.domain.OutboxStatus;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
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
    public void createStockReservedEvent(StockReservedEvent event, Instant createdAt) {
        if(event == null) {
            throw new IllegalArgumentException("Stock reserved event cannot be null");
        }

        JsonNode payload = serialize(event);
        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.STOCK_RESERVED_EVENT,
                payload,
                createdAt
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createStockReservationFailedEvent(
            StockReservationFailedEvent event,
            Instant createdAt
    ) {
        if (event == null) {
            throw new IllegalArgumentException("Stock reservation failed event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.STOCK_RESERVATION_FAILED_EVENT,
                payload,
                createdAt
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createStockReservationConfirmedEvent(
            StockReservationConfirmedEvent event,
            Instant createdAt
    ) {
        if (event == null) {
            throw new IllegalArgumentException("Stock reservation confirmed event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.STOCK_RESERVATION_CONFIRMED_EVENT,
                payload,
                createdAt
        );

        outboxRepository.save(message);
    }

    @Transactional
    public void createStockReservationReleasedEvent(StockReservationReleasedEvent event, Instant createdAt) {
        if (event == null) {
            throw new IllegalArgumentException("Stock reservation released event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.STOCK_RESERVATION_RELEASED_EVENT,
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

    private JsonNode serialize(Object payload) {
        try {
            return jsonMapper.valueToTree(payload);
        } catch (JacksonException exception) {
            throw new OutboxSerializationException("payload could not be serialized", exception);
        }
    }
}
