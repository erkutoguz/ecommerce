package dev.erkut.orderworkflowservice.messaging.kafka.consumer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import dev.erkut.orderworkflowservice.message.event.OrderCheckoutStartedEvent;
import dev.erkut.orderworkflowservice.message.event.StockEventType;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailedEvent;
import dev.erkut.orderworkflowservice.message.event.StockReservedEvent;
import dev.erkut.orderworkflowservice.saga.application.OrderSagaService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StockEventsListener {

    private final OrderSagaService sagaService;
    private final ConsumerUtil consumerUtil;

    public StockEventsListener(OrderSagaService sagaService, ConsumerUtil consumerUtil) {
        this.sagaService = sagaService;
        this.consumerUtil = consumerUtil;
    }

    @KafkaListener(
            topics = "${kafka.topic.stock-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleStockEvents(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);

        StockEventType eventType = StockEventType.from(envelope.messageType());
        if (eventType == null) {
            throw new IllegalArgumentException("Unsupported stock event type: " + envelope.messageType());
        }

        switch (eventType) {
            case STOCK_RESERVED_EVENT -> {
                StockReservedEvent event = consumerUtil.deserialize(envelope.payload(), StockReservedEvent.class);

                sagaService.handleStockReservedEvent(
                        envelope,
                        event
                );
            }
            case STOCK_RESERVATION_FAILED_EVENT -> {
                StockReservationFailedEvent event =
                        consumerUtil.deserialize(envelope.payload(), StockReservationFailedEvent.class);

                sagaService.handleStockReservationFailedEvent(
                        envelope,
                        event
                );
            }
        }
    }
}
