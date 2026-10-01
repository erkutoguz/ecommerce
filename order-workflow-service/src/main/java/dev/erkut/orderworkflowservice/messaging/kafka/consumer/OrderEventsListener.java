package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderConfirmedEvent;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderEventType;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.orderevents.OrderRejectedEvent;
import dev.erkut.orderworkflowservice.observability.metric.SagaMetrics;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventsListener {

    private final OrderSagaService sagaService;
    private final ConsumerUtil consumerUtil;
    private final SagaMetrics sagaMetrics;
    public OrderEventsListener(
            OrderSagaService sagaService,
            ConsumerUtil consumerUtil,
            SagaMetrics sagaMetrics) {
        this.sagaService = sagaService;
        this.consumerUtil = consumerUtil;
        this.sagaMetrics = sagaMetrics;
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
                boolean started = sagaService.handleOrderCheckoutStarted(
                        envelope,
                        event
                );
                if (started) {
                    sagaMetrics.sagaStarted();
                }
            }
            case ORDER_REJECTED_EVENT -> {
                OrderRejectedEvent event =
                        consumerUtil.deserialize(envelope.payload(), OrderRejectedEvent.class);
                boolean failed = sagaService.handleOrderRejectedEvent(
                        envelope,
                        event
                );
                if (failed) {
                    sagaMetrics.sagaFailed();
                }
            }
            case ORDER_CONFIRMED_EVENT -> {
                OrderConfirmedEvent event =
                        consumerUtil.deserialize(envelope.payload(), OrderConfirmedEvent.class);
                boolean completed = sagaService.handleOrderConfirmedEvent(
                        envelope,
                        event
                );
                if (completed) {
                    sagaMetrics.sagaCompleted();
                }
            }
        }
    }
}
