package dev.erkut.orderworkflowservice.messaging.kafka.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaTopicConfigTest {

    private final KafkaTopicsProperties topics = new KafkaTopicsProperties(
            "order.events",
            "stock.commands",
            "stock.events",
            "payment.commands",
            "order.commands",
            "order.events.DLT",
            "stock.events.DLT"
    );

    @Test
    void stockCommandsTopic_shouldUseConfiguredTopicAndPartitions() {
        KafkaTopicConfig config = new KafkaTopicConfig(
                topics
        );

        var topic = config.stockCommandsTopic();

        assertEquals("stock.commands", topic.name());
        assertEquals(3, topic.numPartitions());
    }

    @Test
    void orderCommandsTopic_shouldUseConfiguredTopicAndPartitions() {
        KafkaTopicConfig config = new KafkaTopicConfig(topics);

        var topic = config.orderCommandsTopic();

        assertEquals("order.commands", topic.name());
        assertEquals(3, topic.numPartitions());
    }

    @Test
    void consumedTopicDlts_shouldUseConfiguredTopicsAndPartitions() {
        KafkaTopicConfig config = new KafkaTopicConfig(topics);

        var orderEventsDlt = config.orderEventsDltTopic();
        var stockEventsDlt = config.stockEventsDltTopic();

        assertEquals("order.events.DLT", orderEventsDlt.name());
        assertEquals(3, orderEventsDlt.numPartitions());
        assertEquals("stock.events.DLT", stockEventsDlt.name());
        assertEquals(3, stockEventsDlt.numPartitions());
    }
}
