package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductDeactivatedEvent;
import dev.erkut.stockservice.message.event.ProductEventType;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.message.exception.MessageDeserializationException;
import dev.erkut.stockservice.stock.application.StockService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ProductEventsListener {

    private final StockService stockService;
    private final JsonMapper jsonMapper;
    public ProductEventsListener(StockService stockService, JsonMapper jsonMapper) {
        this.stockService = stockService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(
            topics = "${kafka.topic.product-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenProductEvent(MessageEnvelope envelope) throws JacksonException {
        validateEnvelope(envelope);

        ProductEventType eventType = ProductEventType.from(envelope.messageType());
        if (eventType == null) {
            return;
        }

        switch (eventType) {
            case PRODUCT_CREATED_EVENT -> {
                ProductCreatedEvent event = deserialize(envelope.payload(), ProductCreatedEvent.class);
                stockService.handleProductCreated(envelope, event);
            }
            case PRODUCT_DEACTIVATED_EVENT -> {
                ProductDeactivatedEvent event = deserialize(envelope.payload(), ProductDeactivatedEvent.class);
                stockService.handleProductDeactivated(envelope, event);
            }
        }
    }

    private <T> T deserialize(JsonNode payload, Class<T> type) {
        try {
            return jsonMapper.treeToValue(payload, type);
        } catch (JacksonException exception) {
            throw new MessageDeserializationException("Message payload could not be deserialized", exception);
        }
    }

    private void validateEnvelope(MessageEnvelope envelope) {
        if(envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (envelope.messageId() == null) {
            throw new IllegalArgumentException("Message id cannot be null");
        }
        if (envelope.messageType() == null || envelope.messageType().isBlank()) {
            throw new IllegalArgumentException("Message type cannot be null or blank");
        }
        if (envelope.occurredAt() == null) {
            throw new IllegalArgumentException("Occurrence time cannot be null");
        }
        if (envelope.payload() == null) {
            throw new IllegalArgumentException("Message payload cannot be null");
        }
    }
}
