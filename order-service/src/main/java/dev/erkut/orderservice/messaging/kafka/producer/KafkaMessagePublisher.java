package dev.erkut.orderservice.messaging.kafka.producer;

import dev.erkut.orderservice.message.MessageEnvelope;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class KafkaMessagePublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    public KafkaMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, Object>> publish(
            String topic,
            UUID aggregateId,
            MessageEnvelope envelope
    ) {
        return kafkaTemplate.send(topic, aggregateId.toString(), envelope);
    }
}
