package dev.erkut.orderservice.outbox.application;

import dev.erkut.orderservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.exception.InvalidOutboxMessageException;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

@Service
public class OutboxService {
    private final OutboxMessageRepository outboxMessageRepository;
    private final JsonMapper jsonMapper;
    public OutboxService(OutboxMessageRepository outboxMessageRepository, JsonMapper jsonMapper) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public void createOrderCheckoutStartedMessage(OrderCheckoutStartedEvent event, Instant now) {
        if (event == null) {
            throw new InvalidOutboxMessageException("Event cannot be null");
        }

        JsonNode payload = serialize(event);

        OutboxMessage message = OutboxMessage.create(
                event.orderId(),
                OutboxMessageType.ORDER_CHECKOUT_STARTED,
                payload,
                now
        );

        outboxMessageRepository.save(message);
    }

    private JsonNode serialize(Object event) {
        try {
            return jsonMapper.valueToTree(event);
        } catch (JacksonException ex) {
            throw new OutboxSerializationException("Event couldn't serialized", ex);
        }
    }
}
