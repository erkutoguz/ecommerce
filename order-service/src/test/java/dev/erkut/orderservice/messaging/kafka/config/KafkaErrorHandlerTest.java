package dev.erkut.orderservice.messaging.kafka.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaErrorHandlerTest {

    private static final String ORDER_COMMANDS = "order.commands";
    private static final String ORDER_COMMANDS_DLT = "order.commands.DLT";

    private final KafkaConsumerConfig configuration = new KafkaConsumerConfig();
    private KafkaTemplate<String, Object> kafkaTemplate;
    private Consumer<String, Object> consumer;
    private MessageListenerContainer container;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        consumer = mock(Consumer.class);
        container = mock(MessageListenerContainer.class);
        when(consumer.partitionsFor(any(String.class), any(Duration.class))).thenAnswer(invocation ->
                IntStream.range(0, 3)
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
    void retryableException_shouldRecoverAfterInitialAttemptAndTwoRetries() {
        CommonErrorHandler handler = errorHandler();
        ConsumerRecord<String, Object> record = record(1);
        RuntimeException failure = new RuntimeException("transient failure");

        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertTrue(handler.handleOne(failure, record, consumer, container));

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void nonRetryableException_shouldRecoverAfterFirstFailure() {
        CommonErrorHandler handler = errorHandler();

        assertTrue(handler.handleOne(
                new IllegalArgumentException("invalid command"),
                record(1),
                consumer,
                container
        ));

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    @Test
    void dltRouting_shouldMapOrderCommandsAndKeepPartition() {
        CommonErrorHandler handler = errorHandler();
        int sourcePartition = 2;

        assertTrue(handler.handleOne(
                new IllegalArgumentException("invalid command"),
                record(sourcePartition),
                consumer,
                container
        ));

        ArgumentCaptor<ProducerRecord> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<?, ?> dltRecord = captor.getValue();
        assertEquals(ORDER_COMMANDS_DLT, dltRecord.topic());
        assertEquals(sourcePartition, dltRecord.partition());
    }

    private CommonErrorHandler errorHandler() {
        return configuration.kafkaErrorHandler(
                kafkaTemplate,
                new KafkaTopicsProperties("order.events", ORDER_COMMANDS, ORDER_COMMANDS_DLT)
        );
    }

    private static ConsumerRecord<String, Object> record(int partition) {
        return new ConsumerRecord<>(ORDER_COMMANDS, partition, 4L, "order-id", "payload");
    }
}
