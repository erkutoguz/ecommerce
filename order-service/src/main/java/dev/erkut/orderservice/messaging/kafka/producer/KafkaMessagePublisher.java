package dev.erkut.orderservice.messaging.kafka.producer;

import dev.erkut.orderservice.message.MessageEnvelope;
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
            UUID key,
            MessageEnvelope envelope
    ) {
        return kafkaTemplate.send(topic, key.toString(), envelope);
    }
}
