package dev.erkut.searchservice;

import dev.erkut.searchservice.document.ProductSearchDocument;
import dev.erkut.searchservice.document.ProductStatus;
import dev.erkut.searchservice.message.MessageEnvelope;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeactivatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import dev.erkut.searchservice.persistence.ProductSearchDocumentRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SpringBootTest
@Testcontainers
@Import(SearchServiceApplicationTests.KafkaTopicsTestConfiguration.class)
class SearchServiceApplicationTests {

    private static final String PRODUCT_EVENTS_TOPIC = "product.events";
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"));

    @Container
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.4.5"))
            .withEnv("discovery.type", "single-node")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("xpack.security.http.ssl.enabled", "false")
            .withEnv("xpack.security.transport.ssl.enabled", "false");

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.elasticsearch.uris", () -> "http://" + elasticsearch.getHttpHostAddress());
    }

    @Autowired
    private ProductSearchDocumentRepository productRepository;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private Environment environment;

    @Test
    void contextLoadsWithIndependentSearchConsumerGroup() {
        String searchGroup = environment.getProperty("spring.kafka.consumer.group-id");
        assertEquals("search-service", searchGroup);
        assertNotEquals("stock-service", searchGroup);
    }

    @Test
    void elasticsearchRepositoryRoundTripsStrictlyMappedProductDocument() {
        ProductSearchDocument document = ProductSearchDocument.create(
                PRODUCT_ID,
                "Keyboard",
                new BigDecimal("99.90"),
                ProductStatus.ACTIVE,
                CREATED_AT,
                CREATED_AT
        );

        productRepository.save(document);
        ProductSearchDocument loaded = productRepository.findById(PRODUCT_ID).orElseThrow();

        assertEquals(PRODUCT_ID, loaded.getProductId());
        assertEquals("Keyboard", loaded.getName());
        assertEquals(0, new BigDecimal("99.90").compareTo(loaded.getPrice()));
        assertEquals(ProductStatus.ACTIVE, loaded.getStatus());
        assertEquals(CREATED_AT, loaded.getCreatedAt());
        assertEquals(CREATED_AT, loaded.getUpdatedAt());
        productRepository.deleteById(PRODUCT_ID);
    }

    @Test
    void productLifecycleEventsProjectThroughKafkaIntoElasticsearch() throws Exception {
        send("PRODUCT_CREATED_EVENT", new ProductCreatedEvent(
                PRODUCT_ID, "Keyboard", new BigDecimal("99.90"), ProductStatus.ACTIVE,
                CREATED_AT, CREATED_AT));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ProductSearchDocument loaded = productRepository.findById(PRODUCT_ID).orElseThrow();
            assertEquals("Keyboard", loaded.getName());
            assertEquals(0, new BigDecimal("99.90").compareTo(loaded.getPrice()));
            assertEquals(ProductStatus.ACTIVE, loaded.getStatus());
        });

        Instant updatedAt = CREATED_AT.plusSeconds(60);
        send("PRODUCT_UPDATED_EVENT", new ProductUpdatedEvent(
                PRODUCT_ID, "Mechanical Keyboard", new BigDecimal("109.90"), ProductStatus.ACTIVE,
                CREATED_AT, updatedAt));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ProductSearchDocument loaded = productRepository.findById(PRODUCT_ID).orElseThrow();
            assertEquals("Mechanical Keyboard", loaded.getName());
            assertEquals(0, new BigDecimal("109.90").compareTo(loaded.getPrice()));
            assertEquals(updatedAt, loaded.getUpdatedAt());
        });

        Instant deactivatedAt = updatedAt.plusSeconds(60);
        send("PRODUCT_DEACTIVATED_EVENT", new ProductDeactivatedEvent(
                PRODUCT_ID, "Mechanical Keyboard", new BigDecimal("109.90"), ProductStatus.INACTIVE,
                CREATED_AT, deactivatedAt));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(ProductStatus.INACTIVE,
                        productRepository.findById(PRODUCT_ID).orElseThrow().getStatus()));

        send("PRODUCT_DELETED_EVENT", new ProductDeletedEvent(PRODUCT_ID));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertFalse(productRepository.findById(PRODUCT_ID).isPresent()));

        send("PRODUCT_DELETED_EVENT", new ProductDeletedEvent(PRODUCT_ID));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertFalse(productRepository.findById(PRODUCT_ID).isPresent()));
    }

    private void send(String eventType, Object event) throws Exception {
        var payload = jsonMapper.valueToTree(event);
        var envelope = new MessageEnvelope(UUID.randomUUID(), eventType, Instant.now(), payload);
        kafkaTemplate.send(PRODUCT_EVENTS_TOPIC, PRODUCT_ID.toString(), envelope)
                .get(10, TimeUnit.SECONDS);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class KafkaTopicsTestConfiguration {
        @Bean
        NewTopic productEventsTestTopic() {
            return TopicBuilder.name(PRODUCT_EVENTS_TOPIC).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic productEventsDltTestTopic() {
            return TopicBuilder.name("product.events.DLT").partitions(3).replicas(1).build();
        }
    }
}
