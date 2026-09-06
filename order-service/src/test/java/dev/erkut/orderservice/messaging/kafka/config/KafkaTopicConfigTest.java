package dev.erkut.orderservice.messaging.kafka.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicConfigTest {

    @Test
    void orderEventsTopic_shouldUseConfiguredTopicAndPartitions() {
        KafkaTopicConfig config = new KafkaTopicConfig(
                new KafkaTopicsProperties("order.events")
        );

        var topic = config.orderEventsTopic();

        assertEquals("order.events", topic.name());
        assertEquals(3, topic.numPartitions());
    }
}
