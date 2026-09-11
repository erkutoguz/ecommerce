package dev.erkut.paymentservice.messaging.kafka.routing;

import dev.erkut.paymentservice.messaging.kafka.config.KafkaTopicsProperties;
import dev.erkut.paymentservice.outbox.domain.OutboxMessageType;
import org.springframework.stereotype.Component;

@Component
public class KafkaTopicResolver {

    private final KafkaTopicsProperties topics;

    public KafkaTopicResolver(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    public String resolve(OutboxMessageType messageType) {
        return switch (messageType) {
            case PAYMENT_COMPLETED_EVENT -> topics.paymentEvents();
            case PAYMENT_FAILED_EVENT -> topics.paymentEvents();
        };
    }
}
