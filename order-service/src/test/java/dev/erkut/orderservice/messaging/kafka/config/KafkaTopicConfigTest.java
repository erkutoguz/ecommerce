package dev.erkut.orderservice.messaging.kafka.config;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KafkaTopicConfigTest {

    @Test
    void ownedTopics_shouldDeclareOrderEventsAndOrderCommandsDltButNotOrderCommands() {
        KafkaTopicConfig config = new KafkaTopicConfig(
                new KafkaTopicsProperties(
                        "order.events",
                        "order.commands",
                        "order.commands.DLT"
                )
        );

        var orderEvents = config.orderEventsTopic();
        var orderCommandsDlt = config.orderCommandsDltTopic();

        assertEquals("order.events", orderEvents.name());
        assertEquals(3, orderEvents.numPartitions());
        assertEquals("order.commands.DLT", orderCommandsDlt.name());
        assertEquals(3, orderCommandsDlt.numPartitions());
        assertFalse(Arrays.stream(KafkaTopicConfig.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("orderCommandsTopic")));
    }
}
