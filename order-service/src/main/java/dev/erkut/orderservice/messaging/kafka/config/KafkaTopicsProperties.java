package dev.erkut.orderservice.messaging.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topic")
public record KafkaTopicsProperties(
        String orderEvents,
        String orderCommands,
        String orderCommandsDlt
) {}