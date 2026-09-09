package dev.erkut.paymentservice.messaging.config;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kafka.topic")
public record KafkaTopicsProperties(
        String paymentCommands,
        String paymentEvents,
        String paymentCommandsDlt
) {}