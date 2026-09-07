package dev.erkut.productservice.messaging.kafka.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicConfigTest {

    @Test
    void productEventsTopicUsesConfiguredNameAndPartitions() {
        var topic = new KafkaTopicConfig(new KafkaTopicsProperties("product.events"))
                .productEventsTopic();

        assertEquals("product.events", topic.name());
        assertEquals(3, topic.numPartitions());
    }
}
