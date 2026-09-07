package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductEventType;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.stock.application.StockService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
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
            case PRODUCT_CREATED -> {
                ProductCreatedEvent event = deserializeProductCreated(envelope);
                stockService.handleProductCreated(envelope, event);
            }
        }
    }

    private ProductCreatedEvent deserializeProductCreated(MessageEnvelope envelope) {
        try {
            return jsonMapper.treeToValue(
                    envelope.payload(),
                    ProductCreatedEvent.class
            );
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid PRODUCT_CREATED payload", exception);
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
