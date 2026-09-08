package dev.erkut.stockservice.messaging.kafka.config;

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
    public NewTopic stockEventsTopic() {
        return TopicBuilder
                .name(topics.stockEvents())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic stockCommandsDltTopic() {
        return TopicBuilder
                .name(topics.stockCommandsDlt())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic productEventsDltTopic() {
        return TopicBuilder
                .name(topics.productEventsDlt())
                .partitions(3)
                .build();
    }
}

