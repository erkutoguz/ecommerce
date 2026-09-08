package dev.erkut.stockservice.messaging.kafka.routing;

import dev.erkut.stockservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.stockservice.outbox.domain.OutboxMessageType;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {
    private final KafkaTopicsProperties topics;

    public KafkaTopicResolver(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    public String resolve(OutboxMessageType messageType) {
        return switch (messageType) {

            case STOCK_RESERVED_EVENT, STOCK_RESERVATION_FAILED_EVENT ->
                    topics.stockEvents();
        };
    }
}
