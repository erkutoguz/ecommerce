package dev.erkut.searchservice.messaging.kafka.config;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaErrorHandlerTest {
    private KafkaTemplate<String, Object> kafkaTemplate;
    private Consumer<String, Object> consumer;
    private MessageListenerContainer container;

    @BeforeEach
    void setUp() {
        kafkaTemplate = mock(KafkaTemplate.class);
        consumer = mock(Consumer.class);
        container = mock(MessageListenerContainer.class);
        when(consumer.partitionsFor(any(String.class), any(java.time.Duration.class))).thenAnswer(invocation ->
                IntStream.range(0, 3)
                        .mapToObj(partition -> new PartitionInfo(
                                invocation.getArgument(0), partition, null,
                                new org.apache.kafka.common.Node[0],
                                new org.apache.kafka.common.Node[0]))
                        .toList());
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));
    }

    @Test
    void retryableElasticsearchFailureGetsTwoRetriesThenPublishesToProductDlt() {
        CommonErrorHandler handler = errorHandler();
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "product.events", 2, 17L, "product-id", "payload");
        RuntimeException failure = new RuntimeException("Elasticsearch unavailable");

        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertFalse(handler.handleOne(failure, record, consumer, container));
        assertTrue(handler.handleOne(failure, record, consumer, container));

        var captor = org.mockito.ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        assertTrue(captor.getValue().topic().equals("product.events.DLT"));
        assertTrue(captor.getValue().partition() == 2);
    }

    @Test
    void malformedEventIsSentToDltWithoutRetry() {
        CommonErrorHandler handler = errorHandler();
        ConsumerRecord<String, Object> record = new ConsumerRecord<>(
                "product.events", 0, 1L, "product-id", "payload");

        assertTrue(handler.handleOne(
                new IllegalArgumentException("invalid event"), record, consumer, container));

        verify(kafkaTemplate, times(1)).send(any(ProducerRecord.class));
    }

    private CommonErrorHandler errorHandler() {
        return new KafkaConsumerConfig().kafkaErrorHandler(
                kafkaTemplate,
                new KafkaTopicsProperties("product.events", "product.events.DLT"));
    }
}
