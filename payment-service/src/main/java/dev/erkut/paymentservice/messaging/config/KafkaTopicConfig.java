package dev.erkut.paymentservice.messaging.config;

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
    public NewTopic paymentEventsTopic() {
        return TopicBuilder
                .name(topics.paymentEvents())
                .partitions(3)
                .build();
    }

    @Bean
    public NewTopic paymentCommandsDltTopic() {
        return TopicBuilder
                .name(topics.paymentCommandsDlt())
                .partitions(3)
                .build();
    }
}
