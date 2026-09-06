package dev.erkut.orderworkflowservice.messaging.kafka.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicConfigTest {

    @Test
    void stockCommandsTopic_shouldUseConfiguredTopicAndPartitions() {
        KafkaTopicConfig config = new KafkaTopicConfig(
                new KafkaTopicsProperties("order.events", "stock.commands")
        );

        var topic = config.stockCommandsTopic();

        assertEquals("stock.commands", topic.name());
        assertEquals(3, topic.numPartitions());
    }
}
