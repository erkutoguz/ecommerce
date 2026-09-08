package dev.erkut.stockservice.messaging.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topic")
public record KafkaTopicsProperties(
        String productEvents,
        String stockCommands,
        String stockEvents,
        String stockCommandsDlt,
        String productEventsDlt
) {}
