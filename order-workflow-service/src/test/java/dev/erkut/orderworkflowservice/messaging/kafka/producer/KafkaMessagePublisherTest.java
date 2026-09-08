package dev.erkut.orderworkflowservice.messaging.kafka.producer;

import dev.erkut.orderworkflowservice.message.MessageEnvelope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaMessagePublisherTest {

    private static final String TOPIC = "stock.commands";
    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void publish_shouldUseAggregateIdAsKafkaKeyAndReturnTemplateFuture() {
        MessageEnvelope envelope = new MessageEnvelope(
                UUID.fromString("70000000-0000-0000-0000-000000000001"),
                "RESERVE_STOCK_COMMAND",
                Instant.parse("2026-01-01T10:00:00Z"),
                new JsonMapper().createObjectNode()
        );
        CompletableFuture<SendResult<String, Object>> expected = new CompletableFuture<>();
        KafkaMessagePublisher publisher = new KafkaMessagePublisher(kafkaTemplate);
        when(kafkaTemplate.send(TOPIC, ORDER_ID.toString(), envelope)).thenReturn(expected);

        assertSame(expected, publisher.publish(TOPIC, ORDER_ID, envelope));
        verify(kafkaTemplate).send(TOPIC, ORDER_ID.toString(), envelope);
    }
}
