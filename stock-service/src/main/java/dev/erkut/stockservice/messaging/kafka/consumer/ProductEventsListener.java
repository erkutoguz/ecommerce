package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductDeactivatedEvent;
import dev.erkut.stockservice.message.event.ProductEventType;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.stock.application.StockService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProductEventsListener {

    private final StockService stockService;
    private final ConsumerUtil consumerUtil;
    public ProductEventsListener(
            StockService stockService,
            ConsumerUtil consumerUtil
    ) {
        this.stockService = stockService;
        this.consumerUtil = consumerUtil;
    }

    @KafkaListener(
            topics = "${kafka.topic.product-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenProductEvent(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);

        ProductEventType eventType = ProductEventType.from(envelope.messageType());
        if (eventType == null) {
            return;
        }

        switch (eventType) {
            case PRODUCT_CREATED_EVENT -> {
                ProductCreatedEvent event = consumerUtil.deserialize(envelope.payload(), ProductCreatedEvent.class);
                stockService.handleProductCreated(envelope, event);
            }
            case PRODUCT_DEACTIVATED_EVENT -> {
                ProductDeactivatedEvent event = consumerUtil.deserialize(envelope.payload(), ProductDeactivatedEvent.class);
                stockService.handleProductDeactivated(envelope, event);
            }
        }
    }


}
