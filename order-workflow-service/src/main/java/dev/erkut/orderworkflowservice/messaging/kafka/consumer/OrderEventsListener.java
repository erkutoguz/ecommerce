package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.OrderEventType;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.OrderRejectedEvent;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private final OrderSagaService sagaService;
    private final ConsumerUtil consumerUtil;
    public OrderEventsListener(
            OrderSagaService sagaService,
            ConsumerUtil consumerUtil) {
        this.sagaService = sagaService;
        this.consumerUtil = consumerUtil;
    }

    @KafkaListener(
            topics = "${kafka.topic.order-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleOrderEvents(MessageEnvelope envelope){
        consumerUtil.validateEnvelope(envelope);

        OrderEventType eventType = OrderEventType.from(envelope.messageType());
        if (eventType == null) {
            throw new IllegalArgumentException("Unsupported order event type: " + envelope.messageType());
        }

        switch (eventType) {
            case ORDER_CHECKOUT_STARTED -> {
                OrderCheckoutStartedEvent event =
                        consumerUtil.deserialize(envelope.payload(), OrderCheckoutStartedEvent.class);
                sagaService.handleOrderCheckoutStarted(
                        envelope,
                        event
                );
            }
            case ORDER_REJECTED_EVENT -> {
                OrderRejectedEvent event =
                        consumerUtil.deserialize(envelope.payload(), OrderRejectedEvent.class);
                sagaService.handleOrderRejectedEvent(
                        envelope,
                        event
                );
            }
        }
    }
}
