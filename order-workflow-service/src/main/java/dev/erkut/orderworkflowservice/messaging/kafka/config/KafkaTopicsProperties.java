package dev.erkut.orderworkflowservice.messaging.kafka.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topic")
public record KafkaTopicsProperties(
        String orderEvents,
        String stockCommands,
        String stockEvents,
        String paymentCommands,
        String paymentEvents,
        String paymentEventsDlt,
        String orderCommands,
        String orderEventsDlt,
        String stockEventsDlt
) {}
