package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.OrderEventType;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Component
public class OrderEventsListener {

    private final JsonMapper jsonMapper;
    private final OrderSagaService sagaService;
    public OrderEventsListener(JsonMapper jsonMapper, OrderSagaService sagaService) {
        this.jsonMapper = jsonMapper;
        this.sagaService = sagaService;
    }

    @KafkaListener(
            topics = "${kafka.topic.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenOrder(MessageEnvelope envelope) throws JacksonException {
        validateEnvelope(envelope);

        if (!OrderEventType.ORDER_CHECKOUT_STARTED.name().equals(envelope.messageType())) {
            return;
        }

        OrderCheckoutStartedEvent event =
                deserializeOrderCheckoutStarted(envelope);

        sagaService.handleOrderCheckoutStarted(
                envelope,
                event
        );
    }

    private OrderCheckoutStartedEvent deserializeOrderCheckoutStarted(
            MessageEnvelope envelope
    ) {
        try {
            return jsonMapper.treeToValue(
                    envelope.payload(),
                    OrderCheckoutStartedEvent.class
            );
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Invalid ORDER_CHECKOUT_STARTED payload", exception);
        }
    }

    private void validateEnvelope(MessageEnvelope envelope) {
        if (envelope == null) {
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
