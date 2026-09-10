package dev.erkut.orderworkflowservice.messaging.kafka.config;

import dev.erkut.orderworkflowservice.message.MessageDeserializationException;
import dev.erkut.orderworkflowservice.outbox.application.exception.OutboxSerializationException;
import dev.erkut.orderworkflowservice.outbox.domain.exception.InvalidOutboxMessageException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicsProperties topics
    ) {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) -> {
                            if (record.topic().equals(topics.orderEvents())) {
                                return new TopicPartition(
                                        topics.orderEventsDlt(),
                                        record.partition()
                                );
                            }

                            if (record.topic().equals(topics.stockEvents())) {
                                return new TopicPartition(
                                        topics.stockEventsDlt(),
                                        record.partition()
                                );
                            }

                            if (record.topic().equals(topics.paymentEvents())) {
                                return new TopicPartition(
                                        topics.paymentEventsDlt(),
                                        record.partition()
                                );
                            }

                            throw new IllegalStateException("No DLT configured for topic: " + record.topic());
                        }
                );

        FixedBackOff backOff = new FixedBackOff(
                500L,
                2L
        );

        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(
                        recoverer,
                        backOff
                );

        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                MessageDeserializationException.class,
                InvalidOutboxMessageException.class,
                OutboxSerializationException.class
        );

        return errorHandler;
    }
}
