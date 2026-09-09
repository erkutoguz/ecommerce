package dev.erkut.orderservice.messaging.kafka.routing;

import dev.erkut.orderservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {

    private final KafkaTopicsProperties topics;

    public KafkaTopicResolver(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    public String resolve(OutboxMessageType messageType) {
        return switch (messageType) {

            case ORDER_CHECKOUT_STARTED, ORDER_REJECTED_EVENT ->
                    topics.orderEvents();
        };
    }
}
