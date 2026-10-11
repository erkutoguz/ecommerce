package dev.erkut.searchservice;

import dev.erkut.searchservice.document.ProductSearchDocument;
import dev.erkut.searchservice.document.ProductStatus;
import dev.erkut.searchservice.application.ProductSearchService;
import dev.erkut.searchservice.application.ProductSearchSort;
import dev.erkut.searchservice.message.MessageEnvelope;
import dev.erkut.searchservice.message.event.ProductCreatedEvent;
import dev.erkut.searchservice.message.event.ProductDeactivatedEvent;
import dev.erkut.searchservice.message.event.ProductDeletedEvent;
import dev.erkut.searchservice.message.event.ProductUpdatedEvent;
import dev.erkut.searchservice.persistence.ProductSearchDocumentRepository;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
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
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    private ElasticsearchOperations elasticsearchOperations;

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
