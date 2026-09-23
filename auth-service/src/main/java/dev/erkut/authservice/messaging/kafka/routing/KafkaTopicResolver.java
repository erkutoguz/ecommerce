package dev.erkut.authservice.messaging.kafka.routing;

import dev.erkut.authservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.authservice.outbox.domain.OutboxMessageType;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {

    private final KafkaTopicsProperties topics;
    public KafkaTopicResolver(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    public String resolve(OutboxMessageType messageType) {
        return switch (messageType) {

            case CREATE_CUSTOMER_COMMAND ->
                    topics.customerCommands();
        };
    }

}
