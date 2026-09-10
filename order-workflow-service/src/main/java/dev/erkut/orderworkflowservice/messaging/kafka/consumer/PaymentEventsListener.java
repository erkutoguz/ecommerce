package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.OrderRejectedEvent;
import dev.erkut.orderworkflowservice.message.event.PaymentCompletedEvent;
import dev.erkut.orderworkflowservice.message.event.PaymentEventType;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventsListener {

    private final OrderSagaService sagaService;
    private final ConsumerUtil consumerUtil;
    public PaymentEventsListener(
            OrderSagaService sagaService,
            ConsumerUtil consumerUtil) {
        this.sagaService = sagaService;
        this.consumerUtil = consumerUtil;
    }

    @KafkaListener(
            topics = "${kafka.topic.payment-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handlePaymentEvents(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);

        PaymentEventType eventType = PaymentEventType.from(envelope.messageType());
        if(eventType == null) {
            throw new IllegalArgumentException("Unsupported payment event type: " + envelope.messageType());
        }

        switch (eventType) {
            case PAYMENT_COMPLETED_EVENT -> {
                PaymentCompletedEvent event =
                        consumerUtil.deserialize(envelope.payload(), PaymentCompletedEvent.class);
                sagaService.handlePaymentCompletedEvent(
                        envelope,
                        event
                );
            }

        }
    }
}
