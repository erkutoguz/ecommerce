package dev.erkut.searchservice.messaging.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topic")
public record KafkaTopicsProperties(
        String productEvents,
        String productEventsDlt
) {}
