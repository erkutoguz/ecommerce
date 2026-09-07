package dev.erkut.productservice.messaging.kafka.producer;

import dev.erkut.productservice.message.MessageEnvelope;
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

    @Mock
    private KafkaTemplate<String, MessageEnvelope> kafkaTemplate;

    @Test
    void publishUsesProductIdAsKafkaKey() {
        UUID productId = UUID.fromString("90000000-0000-0000-0000-000000000001");
        MessageEnvelope envelope = new MessageEnvelope(
                UUID.randomUUID(), "PRODUCT_CREATED_EVENT", Instant.now(),
                new JsonMapper().createObjectNode());
        var expected = new CompletableFuture<SendResult<String, MessageEnvelope>>();
        when(kafkaTemplate.send("product.events", productId.toString(), envelope))
                .thenReturn(expected);

        var actual = new KafkaMessagePublisher(kafkaTemplate)
                .publish("product.events", productId, envelope);

        assertSame(expected, actual);
        verify(kafkaTemplate).send("product.events", productId.toString(), envelope);
    }
}
