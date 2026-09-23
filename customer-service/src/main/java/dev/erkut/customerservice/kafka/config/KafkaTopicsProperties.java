package dev.erkut.customerservice.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topic")
public record KafkaTopicsProperties(
        String customerCommands,
        String customerCommandsDlt
) {}
