package dev.erkut.productservice.messaging.kafka.producer;

import dev.erkut.productservice.message.MessageEnvelope;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class KafkaMessagePublisher {

    private final KafkaTemplate<String, MessageEnvelope> kafkaTemplate;

    public KafkaMessagePublisher(KafkaTemplate<String, MessageEnvelope> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, MessageEnvelope>> publish(
            String topic,
            UUID aggregateId,
            MessageEnvelope envelope
    ) {
        if (aggregateId == null) {
            throw new IllegalArgumentException("Aggregate id cannot be null");
        }
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        return kafkaTemplate.send(topic, aggregateId.toString(), envelope);
    }

}
