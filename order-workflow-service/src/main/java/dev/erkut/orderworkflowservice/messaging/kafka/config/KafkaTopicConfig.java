package dev.erkut.orderworkflowservice.messaging.kafka.config;

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
    public NewTopic stockCommandsTopic() {
        return TopicBuilder
                .name(topics.stockCommands())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic orderCommandsTopic() {
        return TopicBuilder
                .name(topics.orderCommands())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic paymentCommandsTopic() {
        return TopicBuilder
                .name(topics.paymentCommands())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic orderEventsDltTopic() {
        return TopicBuilder
                .name(topics.orderEventsDlt())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic stockEventsDltTopic() {
        return TopicBuilder
                .name(topics.stockEventsDlt())
                .partitions(3)
                .build();
    }
}
