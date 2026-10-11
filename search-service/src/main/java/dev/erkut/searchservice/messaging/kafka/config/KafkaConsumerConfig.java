package dev.erkut.searchservice.messaging.kafka.config;

import dev.erkut.searchservice.message.MessageDeserializationException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(KafkaTopicsProperties.class)
public class KafkaConsumerConfig {

    @Bean
    public CommonErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate,
            KafkaTopicsProperties topics
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    if (record.topic().equals(topics.productEvents())) {
                        return new TopicPartition(topics.productEventsDlt(), record.partition());
                    }

                    throw new IllegalStateException("No DLT configured for topic: " + record.topic());
                }
        );

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(500L, 2L)
        );
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                MessageDeserializationException.class
        );
        return errorHandler;
    }
}
