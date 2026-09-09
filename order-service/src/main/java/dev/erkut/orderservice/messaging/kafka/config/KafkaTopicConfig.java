package dev.erkut.orderservice.messaging.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaTopicConfig {

    private final KafkaTopicsProperties topics;

    public KafkaTopicConfig(KafkaTopicsProperties topics) {
        this.topics = topics;
    }

    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder
                .name(topics.orderEvents())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic orderCommandsDltTopic() {
        return TopicBuilder
                .name(topics.orderCommandsDlt())
                .partitions(3)
                .build();
    }
}
