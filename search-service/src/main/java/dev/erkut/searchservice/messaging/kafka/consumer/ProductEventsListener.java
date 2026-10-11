package dev.erkut.searchservice.messaging.kafka.consumer;

import dev.erkut.searchservice.application.ProductIndexService;
import dev.erkut.searchservice.message.MessageEnvelope;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeactivatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductEventType;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ProductEventsListener {
    private static final Logger log = LoggerFactory.getLogger(ProductEventsListener.class);

    private final ConsumerUtil consumerUtil;
    private final ProductIndexService productIndexService;

    public ProductEventsListener(ConsumerUtil consumerUtil, ProductIndexService productIndexService) {
        this.consumerUtil = consumerUtil;
        this.productIndexService = productIndexService;
    }

    @KafkaListener(
            topics = "${kafka.topic.product-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenProductEvent(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);
        log.info("Received Product event: messageId={}, messageType={}", envelope.messageId(), envelope.messageType());

        ProductEventType eventType = ProductEventType.from(envelope.messageType());
        if (eventType == null) {
            throw new IllegalArgumentException("Unsupported Product event type: " + envelope.messageType());
        }

        switch (eventType) {
            case PRODUCT_CREATED_EVENT -> productIndexService.handleCreated(
                    consumerUtil.deserialize(envelope.payload(), ProductCreatedEvent.class));
            case PRODUCT_UPDATED_EVENT -> productIndexService.handleUpdated(
                    consumerUtil.deserialize(envelope.payload(), ProductUpdatedEvent.class));
            case PRODUCT_DEACTIVATED_EVENT -> productIndexService.handleDeactivated(
                    consumerUtil.deserialize(envelope.payload(), ProductDeactivatedEvent.class));
            case PRODUCT_DELETED_EVENT -> productIndexService.handleDeleted(
                    consumerUtil.deserialize(envelope.payload(), ProductDeletedEvent.class));
        }
    }
}
