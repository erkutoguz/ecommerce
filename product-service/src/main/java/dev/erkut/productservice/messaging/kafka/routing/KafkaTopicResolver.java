package dev.erkut.productservice.messaging.kafka.routing;

import dev.erkut.productservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.productservice.outbox.domain.OutboxMessageType;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {
    private final KafkaTopicsProperties topics;

    public KafkaTopicResolver(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    public String resolve(OutboxMessageType messageType) {
        return switch (messageType) {

            case PRODUCT_CREATED_EVENT ->
                    topics.productEvents();
        };
    }
}
