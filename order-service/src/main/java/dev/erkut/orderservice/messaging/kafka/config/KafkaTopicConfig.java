package dev.erkut.orderservice.messaging.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    private final String ORDER_EVENTS_TOPIC;
    public KafkaTopicConfig(@Value("${kafka.order.topic}") String ORDER_EVENTS_TOPIC) {
        this.ORDER_EVENTS_TOPIC = ORDER_EVENTS_TOPIC;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
