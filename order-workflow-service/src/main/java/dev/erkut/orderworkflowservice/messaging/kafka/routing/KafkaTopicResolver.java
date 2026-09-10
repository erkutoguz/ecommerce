package dev.erkut.orderworkflowservice.messaging.kafka.routing;

import dev.erkut.orderworkflowservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {

    private final KafkaTopicsProperties topics;

    public KafkaTopicResolver(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    public String resolve(OutboxMessageType messageType) {
        return switch (messageType) {

            case RESERVE_STOCK_COMMAND, CONFIRM_STOCK_RESERVATION_COMMAND ->
                    topics.stockCommands();
            case REJECT_ORDER_COMMAND ->
                    topics.orderCommands();
            case INITIATE_PAYMENT_COMMAND ->
                    topics.paymentCommands();
        };
    }
}
