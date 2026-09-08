package dev.erkut.stockservice.messaging.kafka.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

class KafkaErrorHandlerTest {

    private static final String PRODUCT_EVENTS = "product.events";
    private static final String STOCK_COMMANDS = "stock.commands";
    private static final String STOCK_COMMANDS_DLT = "stock.commands.DLT";
    private static final String PRODUCT_EVENTS_DLT = "product.events.DLT";

    private final KafkaConsumerConfig configuration = new KafkaConsumerConfig();
    private KafkaTemplate<String, Object> kafkaTemplate;
    private Consumer<String, Object> consumer;
    private MessageListenerContainer container;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        consumer = mock(Consumer.class);
        container = mock(MessageListenerContainer.class);
        when(consumer.partitionsFor(any(String.class), any(java.time.Duration.class))).thenAnswer(invocation ->
                java.util.stream.IntStream.range(0, 3)
                        .mapToObj(partition -> new PartitionInfo(
                                invocation.getArgument(0),
                                partition,
                                null,
                                new org.apache.kafka.common.Node[0],
                                new org.apache.kafka.common.Node[0]
                        ))
                        .toList()
        );
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));
    }

    @Test
    void retryableExceptionGetsInitialAttemptAndTwoRetriesBeforeRecovery() {
        CommonErrorHandler handler = errorHandler();
        ConsumerRecord<String, Object> record = record(STOCK_COMMANDS);
        RuntimeException failure = new RuntimeException("transient failure");

        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertTrue(handler.handleOne(failure, record, consumer, container));

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void nonRetryableExceptionIsRecoveredAfterTheFirstFailure() {
        CommonErrorHandler handler = errorHandler();
        ConsumerRecord<String, Object> record = record(STOCK_COMMANDS);

        assertTrue(handler.handleOne(
                new IllegalArgumentException("invalid command"),
                record,
                consumer,
                container
        ));

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @ParameterizedTest
    @MethodSource("dltDestinations")
    void dltDestinationKeepsSourceTopicMappingAndPartition(
            String sourceTopic,
            String dltTopic
    ) {
        CommonErrorHandler handler = errorHandler();
        int sourcePartition = 2;
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                sourceTopic,
                sourcePartition,
                17L,
                "aggregate-key",
                "payload"
        );

        assertTrue(handler.handleOne(
                new IllegalArgumentException("non-retryable"),
                record,
                consumer,
                container
        ));

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<?, ?> dltRecord = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(dltTopic, dltRecord.topic());
        org.junit.jupiter.api.Assertions.assertEquals(sourcePartition, dltRecord.partition());
    }

    private CommonErrorHandler errorHandler() {
        return configuration.kafkaErrorHandler(
                kafkaTemplate,
                new KafkaTopicsProperties(
                        PRODUCT_EVENTS,
                        STOCK_COMMANDS,
                        "stock.events",
                        STOCK_COMMANDS_DLT,
                        PRODUCT_EVENTS_DLT
                )
        );
    }

    private static ConsumerRecord<String, Object> record(String topic) {
        return new ConsumerRecord<>(topic, 1, 4L, "key", "value");
    }

    private static Stream<Arguments> dltDestinations() {
        return Stream.of(
                Arguments.of(STOCK_COMMANDS, STOCK_COMMANDS_DLT),
                Arguments.of(PRODUCT_EVENTS, PRODUCT_EVENTS_DLT)
        );
    }
}
