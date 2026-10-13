package dev.erkut.searchservice;

import dev.erkut.searchservice.document.ProductSearchDocument;
import dev.erkut.searchservice.document.ProductStatus;
import dev.erkut.searchservice.application.ProductSearchService;
import dev.erkut.searchservice.application.ProductSearchSort;
import dev.erkut.searchservice.config.ElasticsearchIndexInitializer;
import dev.erkut.searchservice.message.MessageEnvelope;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeactivatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import dev.erkut.searchservice.messaging.kafka.consumer.ProductEventsListener;
import dev.erkut.searchservice.persistence.ProductSearchDocumentRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.index.Settings;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Import(SearchServiceApplicationTests.KafkaTopicsTestConfiguration.class)
class SearchServiceApplicationTests {

    private static final String PRODUCT_EVENTS_TOPIC = "product.events";
    private static final UUID PRODUCT_ID = UUID.fromString("90000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID IPHONE_16_PRO_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID IPHONE_15_ID = UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID IPHONE_CASE_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID GALAXY_ID = UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID INACTIVE_IPHONE_ID = UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final Instant SEARCH_BASE_TIME = Instant.parse("2026-05-01T10:00:00Z");

    @Container
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:4.1.1"));

    @Container
    static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            DockerImageName.parse("docker.elastic.co/elasticsearch/elasticsearch:9.4.5"))
            .withEnv("ES_JAVA_OPTS", "-Xms1g -Xmx1g")
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

    @Autowired
    private ProductSearchService productSearchService;

    @Autowired
    private dev.erkut.searchservice.application.ProductIndexService productIndexService;

    @Autowired
    private ProductEventsListener productEventsListener;

    @Autowired
    private ElasticsearchOperations elasticsearchOperations;

    @Autowired
    private ElasticsearchIndexInitializer indexInitializer;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void seedSearchProducts() {
        productRepository.deleteAll();
        productRepository.saveAll(List.of(
                searchDocument(IPHONE_16_PRO_ID, "Apple iPhone 16 Pro", "65000", ProductStatus.ACTIVE, 5),
                searchDocument(IPHONE_15_ID, "Apple iPhone 15", "45000", ProductStatus.ACTIVE, 4),
                searchDocument(IPHONE_CASE_ID, "iPhone Protective Case", "1500", ProductStatus.ACTIVE, 3),
                searchDocument(GALAXY_ID, "Samsung Galaxy S26", "60000", ProductStatus.ACTIVE, 2),
                searchDocument(INACTIVE_IPHONE_ID, "Old iPhone", "10000", ProductStatus.INACTIVE, 1)
        ));
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();
    }

    @Test
    void contextLoadsWithIndependentSearchConsumerGroup() {
        String searchGroup = environment.getProperty("spring.kafka.consumer.group-id");
        assertEquals("search-service", searchGroup);
        assertNotEquals("stock-service", searchGroup);
    }

    @Test
    void managementEndpointsUseTheInternalPortAndExposePrometheusMetrics() {
        assertEquals("9090", environment.getProperty("management.server.port"));
        assertEquals("health,prometheus", environment.getProperty("management.endpoints.web.exposure.include"));
        assertEquals("true", environment.getProperty(
                "management.metrics.distribution.percentiles-histogram.http.server.requests"));
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
    void existingStrictIndexMappingAcceptsVersionAndDeleteFieldsAdditively() throws Exception {
        IndexOperations indexOperations = elasticsearchOperations.indexOps(ProductSearchDocument.class);
        indexOperations.delete();
        String oldMappingJson = """
                {
                  "dynamic": "strict",
                  "properties": {
                    "productId": {"type": "keyword"},
                    "name": {"type": "text"},
                    "price": {"type": "scaled_float", "scaling_factor": 100},
                    "status": {"type": "keyword"},
                    "createdAt": {"type": "date"},
                    "updatedAt": {"type": "date"}
                  }
                }
                """;
        boolean created = indexOperations.create(
                Settings.parse("""
                        {"index":{"number_of_shards":1,"number_of_replicas":0}}
                        """), Document.parse(oldMappingJson));
        assertTrue(created);

        try {
            indexInitializer.run(new DefaultApplicationArguments());
            String upgradedMapping = indexOperations.getMapping().toString();
            assertTrue(upgradedMapping.contains("version"));
            assertTrue(upgradedMapping.contains("deleted"));
        } finally {
            indexOperations.delete();
            indexInitializer.run(new DefaultApplicationArguments());
        }
    }

    @Test
    void productLifecycleEventsProjectThroughKafkaIntoElasticsearch() throws Exception {
        UUID productId = UUID.randomUUID();
        send("PRODUCT_CREATED_EVENT", new ProductCreatedEvent(
                productId, "Keyboard", new BigDecimal("99.90"), ProductStatus.ACTIVE,
                CREATED_AT, CREATED_AT, 0));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ProductSearchDocument loaded = requireProductDocument(productId);
            assertEquals("Keyboard", loaded.getName());
            assertEquals(0, new BigDecimal("99.90").compareTo(loaded.getPrice()));
            assertEquals(ProductStatus.ACTIVE, loaded.getStatus());
            assertEquals(0L, loaded.getVersion());
        });

        Instant updatedAt = CREATED_AT.plusSeconds(60);
        send("PRODUCT_UPDATED_EVENT", new ProductUpdatedEvent(
                productId, "Mechanical Keyboard", new BigDecimal("109.90"), ProductStatus.ACTIVE,
                CREATED_AT, updatedAt, 1));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ProductSearchDocument loaded = requireProductDocument(productId);
            assertEquals("Mechanical Keyboard", loaded.getName());
            assertEquals(0, new BigDecimal("109.90").compareTo(loaded.getPrice()));
            assertEquals(updatedAt, loaded.getUpdatedAt());
            assertEquals(1L, loaded.getVersion());
        });

        Instant deactivatedAt = updatedAt.plusSeconds(60);
        send("PRODUCT_DEACTIVATED_EVENT", new ProductDeactivatedEvent(
                productId, "Mechanical Keyboard", new BigDecimal("109.90"), ProductStatus.INACTIVE,
                CREATED_AT, deactivatedAt, 2));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        {
            ProductSearchDocument loaded = requireProductDocument(productId);
            assertEquals(ProductStatus.INACTIVE, loaded.getStatus());
            assertEquals(2L, loaded.getVersion());
        });

        send("PRODUCT_DELETED_EVENT", new ProductDeletedEvent(productId, 3));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        {
            ProductSearchDocument loaded = requireProductDocument(productId);
            assertTrue(loaded.isDeleted());
            assertEquals(3L, loaded.getVersion());
        });

        send("PRODUCT_DELETED_EVENT", new ProductDeletedEvent(productId, 3));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        {
            ProductSearchDocument loaded = requireProductDocument(productId);
            assertTrue(loaded.isDeleted());
            assertEquals(3L, loaded.getVersion());
        });
    }

    @Test
    void staleProductEventsAndUpdatesAfterDeleteCannotRevertSearchState() throws Exception {
        UUID productId = UUID.randomUUID();
        Instant v1At = CREATED_AT.plusSeconds(1);
        Instant v2At = CREATED_AT.plusSeconds(2);
        send("PRODUCT_UPDATED_EVENT", new ProductUpdatedEvent(productId, "Newest", new BigDecimal("200.00"),
                ProductStatus.ACTIVE, CREATED_AT, v2At, 2));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertEquals(2L, requireProductDocument(productId).getVersion()));

        send("PRODUCT_UPDATED_EVENT", new ProductUpdatedEvent(productId, "Stale", new BigDecimal("100.00"),
                ProductStatus.ACTIVE, CREATED_AT, v1At, 1));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ProductSearchDocument current = requireProductDocument(productId);
            assertEquals("Newest", current.getName());
            assertEquals(2L, current.getVersion());
        });

        send("PRODUCT_DELETED_EVENT", new ProductDeletedEvent(productId, 3));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertTrue(requireProductDocument(productId).isDeleted()));
        send("PRODUCT_UPDATED_EVENT", new ProductUpdatedEvent(productId, "Resurrected", new BigDecimal("100.00"),
                ProductStatus.ACTIVE, CREATED_AT, v1At, 2));
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
            ProductSearchDocument current = requireProductDocument(productId);
            assertTrue(current.isDeleted());
            assertEquals(3L, current.getVersion());
        });
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();
        assertFalse(productSearchService.search(null, null, null, 0, 100, null).getContent().stream()
                .anyMatch(product -> product.productId().equals(productId)));
    }

    @Test
    void legacyProductEventWithoutVersionRemainsDeserializable() throws Exception {
        var legacyPayload = jsonMapper.readTree("""
                {"productId":"90000000-0000-0000-0000-000000000001","name":"Legacy keyboard",
                 "price":99.90,"status":"ACTIVE","createdAt":"2026-01-01T10:00:00Z",
                 "updatedAt":"2026-01-01T10:00:00Z"}
                """);
        var envelope = new MessageEnvelope(UUID.randomUUID(), "PRODUCT_UPDATED_EVENT", Instant.now(), legacyPayload);
        productEventsListener.listenProductEvent(envelope);
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();

        ProductSearchDocument current = productRepository.findById(PRODUCT_ID).orElseThrow();
        assertEquals("Legacy keyboard", current.getName());
        assertEquals(0L, current.getVersion());
    }

    @Test
    void concurrentProjectionUpdatesAlwaysKeepTheHighestVersion() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<?> older = workers.submit(() -> {
                awaitAndApply(ready, start, new ProductUpdatedEvent(PRODUCT_ID, "v4", new BigDecimal("4.00"),
                        ProductStatus.ACTIVE, CREATED_AT, CREATED_AT.plusSeconds(4), 4));
                return null;
            });
            Future<?> newer = workers.submit(() -> {
                awaitAndApply(ready, start, new ProductUpdatedEvent(PRODUCT_ID, "v5", new BigDecimal("5.00"),
                        ProductStatus.ACTIVE, CREATED_AT, CREATED_AT.plusSeconds(5), 5));
                return null;
            });
            ready.await();
            start.countDown();
            older.get();
            newer.get();
        }
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();
        ProductSearchDocument current = productRepository.findById(PRODUCT_ID).orElseThrow();
        assertEquals(5L, current.getVersion());
        assertEquals("v5", current.getName());
    }

    @Test
    void legacyDocumentDoesNotTreatMissingVersionAsOlderThanReplayVersionZero() {
        Map<String, Object> legacySource = new HashMap<>(Map.of(
                "productId", PRODUCT_ID.toString(),
                "name", "Legacy current state",
                "price", new BigDecimal("200.00"),
                "status", "ACTIVE",
                "createdAt", CREATED_AT.toString(),
                "updatedAt", CREATED_AT.toString()
        ));
        elasticsearchOperations.index(IndexQuery.builder()
                        .withId(PRODUCT_ID.toString())
                        .withObject(legacySource)
                        .build(), IndexCoordinates.of("products"));
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();

        productIndexService.handleUpdated(new ProductUpdatedEvent(PRODUCT_ID, "Old replay", new BigDecimal("100.00"),
                ProductStatus.ACTIVE, CREATED_AT, CREATED_AT.plusSeconds(1), 0));
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();
        assertEquals("Legacy current state", productRepository.findById(PRODUCT_ID).orElseThrow().getName());

        productIndexService.handleUpdated(new ProductUpdatedEvent(PRODUCT_ID, "Fresh version", new BigDecimal("250.00"),
                ProductStatus.ACTIVE, CREATED_AT, CREATED_AT.plusSeconds(2), 1));
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();
        ProductSearchDocument updated = productRepository.findById(PRODUCT_ID).orElseThrow();
        assertEquals("Fresh version", updated.getName());
        assertEquals(1L, updated.getVersion());
    }

    @Test
    void legacyDeleteTombstoneRejectsLaterVersionZeroActiveReplay() {
        Map<String, Object> legacySource = new HashMap<>(Map.of(
                "productId", PRODUCT_ID.toString(),
                "name", "Legacy current state",
                "price", new BigDecimal("200.00"),
                "status", "ACTIVE",
                "createdAt", CREATED_AT.toString(),
                "updatedAt", CREATED_AT.toString()
        ));
        elasticsearchOperations.index(IndexQuery.builder()
                        .withId(PRODUCT_ID.toString())
                        .withObject(legacySource)
                        .build(), IndexCoordinates.of("products"));
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();

        productIndexService.handleDeleted(new ProductDeletedEvent(PRODUCT_ID, 0));
        productIndexService.handleUpdated(new ProductUpdatedEvent(PRODUCT_ID, "Old replay", new BigDecimal("100.00"),
                ProductStatus.ACTIVE, CREATED_AT, CREATED_AT.plusSeconds(1), 0));
        elasticsearchOperations.indexOps(ProductSearchDocument.class).refresh();

        ProductSearchDocument tombstone = productRepository.findById(PRODUCT_ID).orElseThrow();
        assertTrue(tombstone.isDeleted());
        assertEquals(0L, tombstone.getVersion());
    }

    private void awaitAndApply(CountDownLatch ready, CountDownLatch start, ProductUpdatedEvent event) {
        try {
            ready.countDown();
            start.await();
            productIndexService.handleUpdated(event);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void nameSearchFiltersInactiveProductsAndRanksTheBestMatchFirst() {
        Page<dev.erkut.searchservice.api.response.ProductSearchResponse> results =
                productSearchService.search(" iphone pro ", null, null, 0, 10, null);

        assertEquals(3, results.getTotalElements());
        assertEquals(IPHONE_16_PRO_ID, results.getContent().getFirst().productId());
        assertFalse(results.getContent().stream()
                .anyMatch(product -> product.productId().equals(INACTIVE_IPHONE_ID)));

        Page<dev.erkut.searchservice.api.response.ProductSearchResponse> iphoneResults =
                productSearchService.search("iphone", null, null, 0, 10, null);
        assertEquals(3, iphoneResults.getTotalElements());
    }

    @Test
    void inclusiveMinimumMaximumAndCombinedPriceRangesAreApplied() {
        var minimumResults = productSearchService.search(
                null, new BigDecimal("45000"), null, 0, 10, null);
        assertEquals(List.of(IPHONE_16_PRO_ID, IPHONE_15_ID, GALAXY_ID),
                minimumResults.getContent().stream().map(product -> product.productId()).toList());

        var maximumResults = productSearchService.search(
                null, null, new BigDecimal("45000"), 0, 10, null);
        assertEquals(List.of(IPHONE_15_ID, IPHONE_CASE_ID),
                maximumResults.getContent().stream().map(product -> product.productId()).toList());

        var combinedResults = productSearchService.search(
                null, new BigDecimal("45000"), new BigDecimal("60000"), 0, 10, null);
        assertEquals(List.of(IPHONE_15_ID, GALAXY_ID),
                combinedResults.getContent().stream().map(product -> product.productId()).toList());
    }

    @Test
    void missingAndBlankQueriesReturnActiveProductsNewestFirstWithPagination() {
        var firstPage = productSearchService.search(null, null, null, 0, 2, null);
        assertEquals(4, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(List.of(IPHONE_16_PRO_ID, IPHONE_15_ID),
                firstPage.getContent().stream().map(product -> product.productId()).toList());

        var secondPage = productSearchService.search("   ", null, null, 1, 2, null);
        assertEquals(List.of(IPHONE_CASE_ID, GALAXY_ID),
                secondPage.getContent().stream().map(product -> product.productId()).toList());
        assertFalse(secondPage.getContent().stream()
                .anyMatch(product -> product.productId().equals(INACTIVE_IPHONE_ID)));
    }

    @Test
    void controlledSortValuesOrderResultsAsSpecified() {
        var priceAscending = productSearchService.search(
                null, null, null, 0, 10, ProductSearchSort.PRICE_ASC);
        assertEquals(List.of(IPHONE_CASE_ID, IPHONE_15_ID, GALAXY_ID, IPHONE_16_PRO_ID),
                priceAscending.getContent().stream().map(product -> product.productId()).toList());

        var priceDescending = productSearchService.search(
                null, null, null, 0, 10, ProductSearchSort.PRICE_DESC);
        assertEquals(List.of(IPHONE_16_PRO_ID, GALAXY_ID, IPHONE_15_ID, IPHONE_CASE_ID),
                priceDescending.getContent().stream().map(product -> product.productId()).toList());

        var newest = productSearchService.search(null, null, null, 0, 10, ProductSearchSort.NEWEST);
        assertEquals(IPHONE_16_PRO_ID, newest.getContent().getFirst().productId());

        var relevance = productSearchService.search(
                "iphone pro", null, null, 0, 10, ProductSearchSort.RELEVANCE);
        assertEquals(IPHONE_16_PRO_ID, relevance.getContent().getFirst().productId());
    }

    @Test
    void invalidRangesAndPaginationWindowAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                productSearchService.search(null, new BigDecimal("-1"), null, 0, 10, null));
        assertThrows(IllegalArgumentException.class, () ->
                productSearchService.search(null, null, new BigDecimal("-1"), 0, 10, null));
        assertThrows(IllegalArgumentException.class, () ->
                productSearchService.search(null, new BigDecimal("20"), new BigDecimal("10"), 0, 10, null));
        assertThrows(IllegalArgumentException.class, () ->
                productSearchService.search(null, null, null, 100, 100, null));
        assertThrows(IllegalArgumentException.class, () ->
                productSearchService.search(null, null, null, Integer.MAX_VALUE, 100, null));
        assertThrows(IllegalArgumentException.class, () ->
                productSearchService.search(" ", null, null, 0, 10, ProductSearchSort.RELEVANCE));
    }

    @Test
    void searchServiceAllowsPublicGetAndDeniesMutationMethods() throws Exception {
        mockMvc.perform(get("/search/products"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/search/products"))
                .andExpect(status().isForbidden());
    }

    private ProductSearchDocument searchDocument(
            UUID productId,
            String name,
            String price,
            ProductStatus status,
            long createdOffsetDays
    ) {
        Instant createdAt = SEARCH_BASE_TIME.plusSeconds(createdOffsetDays * 86_400);
        return ProductSearchDocument.create(
                productId,
                name,
                new BigDecimal(price),
                status,
                createdAt,
                createdAt
        );
    }

    private ProductSearchDocument requireProductDocument(UUID productId) {
        ProductSearchDocument document = productRepository.findById(productId).orElse(null);
        assertNotNull(document, "Expected Product event to be projected into Elasticsearch");
        return document;
    }

    private void send(String eventType, Object event) throws Exception {
        var payload = jsonMapper.valueToTree(event);
        var envelope = new MessageEnvelope(UUID.randomUUID(), eventType, Instant.now(), payload);
        UUID productId = jsonMapper.convertValue(payload.get("productId"), UUID.class);
        kafkaTemplate.send(PRODUCT_EVENTS_TOPIC, productId.toString(), envelope)
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
