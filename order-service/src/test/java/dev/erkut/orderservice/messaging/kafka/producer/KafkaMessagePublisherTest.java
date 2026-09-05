package dev.erkut.orderservice.messaging.kafka.producer;

import dev.erkut.orderservice.message.MessageEnvelope;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaMessagePublisherTest {

    private static final String TOPIC = "order.events";
    private static final UUID AGGREGATE_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final JsonNode PAYLOAD = new JsonMapper().readTree("{\"orderId\":\"test\"}");

    @Mock
    private KafkaTemplate<String, MessageEnvelope> kafkaTemplate;

    @Test
    void publish_shouldDelegateToKafkaTemplateAndReturnSameFuture() {
        MessageEnvelope envelope = new MessageEnvelope(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                "ORDER_CHECKOUT_STARTED",
                Instant.parse("2026-01-01T10:00:00Z"),
                PAYLOAD
        );
        CompletableFuture<SendResult<String, MessageEnvelope>> expectedFuture =
                new CompletableFuture<>();
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(kafkaTemplate);

        when(kafkaTemplate.send(TOPIC, AGGREGATE_ID.toString(), envelope))
                .thenReturn(expectedFuture);

        CompletableFuture<SendResult<String, MessageEnvelope>> actualFuture =
                publisher.publish(TOPIC, AGGREGATE_ID, envelope);

        assertSame(expectedFuture, actualFuture);
        verify(kafkaTemplate).send(TOPIC, AGGREGATE_ID.toString(), envelope);
    }
}
