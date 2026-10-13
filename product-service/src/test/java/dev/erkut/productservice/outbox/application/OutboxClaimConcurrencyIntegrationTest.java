package dev.erkut.productservice.outbox.application;

import dev.erkut.productservice.message.event.ProductCreatedEvent;
import dev.erkut.productservice.message.event.ProductUpdatedEvent;
import dev.erkut.productservice.outbox.domain.OutboxMessage;
import dev.erkut.productservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.productservice.observability.tracing.OutboxTraceContext;
import dev.erkut.productservice.product.domain.ProductStatus;
import dev.erkut.productservice.product.domain.Product;
import dev.erkut.productservice.product.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class OutboxClaimConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OutboxService outboxService;
    @Autowired OutboxMessageRepository repository;
    @Autowired ProductRepository productRepository;
    @Autowired PlatformTransactionManager transactionManager;

    @MockitoBean
    OutboxTraceContext traceContext;

    @BeforeEach
    void cleanOutbox() {
        repository.deleteAll();
    }

    @Test
    void twoWorkersCannotOwnTheSamePendingMessage() throws Exception {
        UUID aggregateId = UUID.randomUUID();
        create(aggregateId, Instant.parse("2026-01-01T00:00:00Z"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<List<ClaimedOutboxMessage>> first = workers.submit(() -> claimWhenReleased(ready, start));
            Future<List<ClaimedOutboxMessage>> second = workers.submit(() -> claimWhenReleased(ready, start));
            ready.await();
            start.countDown();
            List<ClaimedOutboxMessage> a = first.get();
            List<ClaimedOutboxMessage> b = second.get();
            assertEquals(1, a.size() + b.size());
        }
    }

    @Test
    void twoWorkersCanClaimDifferentAggregatesConcurrently() throws Exception {
        OutboxMessage firstMessage = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        OutboxMessage secondMessage = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:01Z"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<List<ClaimedOutboxMessage>> first = workers.submit(() -> claimWhenReleased(ready, start));
            Future<List<ClaimedOutboxMessage>> second = workers.submit(() -> claimWhenReleased(ready, start));
            ready.await();
            start.countDown();
            List<ClaimedOutboxMessage> claims = new ArrayList<>(first.get());
            claims.addAll(second.get());
            assertEquals(2, claims.size());
            assertEquals(2, claims.stream().map(c -> c.message().getId()).distinct().count());
            assertTrue(claims.stream().anyMatch(c -> c.message().getId().equals(firstMessage.getId())));
            assertTrue(claims.stream().anyMatch(c -> c.message().getId().equals(secondMessage.getId())));
        }
    }

    @Test
    void differentAggregatesProgressAndSameAggregateRemainsHeadOfLineOrdered() {
        UUID firstAggregate = UUID.randomUUID();
        UUID secondAggregate = UUID.randomUUID();
        OutboxMessage first = create(firstAggregate, Instant.parse("2026-01-01T00:00:00Z"));
        create(firstAggregate, Instant.parse("2026-01-01T00:00:01Z"));
        create(secondAggregate, Instant.parse("2026-01-01T00:00:02Z"));

        List<ClaimedOutboxMessage> claimed = outboxService.claimMessages(10, 180);
        assertEquals(2, claimed.size());
        assertEquals(2, claimed.stream().map(c -> c.message().getAggregateId()).distinct().count());
        assertTrue(claimed.stream().anyMatch(c -> c.message().getId().equals(first.getId())));
    }

    @Test
    void expiredClaimCanBeTakenOverAndOldTokenCannotCompleteIt() {
        OutboxMessage message = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        ClaimedOutboxMessage original = outboxService.claimMessages(1, 0).getFirst();
        ClaimedOutboxMessage takeover = outboxService.claimMessages(1, 180).getFirst();

        assertEquals(message.getId(), takeover.message().getId());
        assertNotEquals(original.claimToken(), takeover.claimToken());
        assertTrue(!outboxService.markPublished(message.getId(), original.claimToken(), Instant.now()));
        assertTrue(outboxService.markPublished(message.getId(), takeover.claimToken(), Instant.now()));
        assertEquals("PUBLISHED", repository.findById(message.getId()).orElseThrow().getStatus().name());
    }

    @Test
    void failedPublishCanBeRescheduledAndKeepsStableMessageIdentity() {
        OutboxMessage message = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        ClaimedOutboxMessage claim = outboxService.claimMessages(1, 180).getFirst();
        assertTrue(outboxService.reschedule(message.getId(), claim.claimToken(), Instant.now().minusSeconds(1)));
        ClaimedOutboxMessage retry = outboxService.claimMessages(1, 180).getFirst();

        assertEquals(message.getId(), retry.message().getId());
        assertNotEquals(claim.claimToken(), retry.claimToken());
    }

    @Test
    void rolledBackClaimDoesNotBecomeCommittedOwnership() {
        OutboxMessage message = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        new TransactionTemplate(transactionManager).execute(status -> {
            assertEquals(1, outboxService.claimMessages(1, 180).size());
            status.setRollbackOnly();
            return null;
        });

        assertEquals("PENDING", repository.findById(message.getId()).orElseThrow().getStatus().name());
        assertEquals(0, repository.countByStatus(dev.erkut.productservice.outbox.domain.OutboxStatus.IN_PROGRESS));
    }

    @Test
    void concurrentProductUpdatesCommitOneMonotonicVersionAndOneOutboxEvent() throws Exception {
        Product seed = productRepository.saveAndFlush(
                Product.create("Product", new BigDecimal("10.00"), Instant.parse("2026-01-01T00:00:00Z")));
        UUID productId = seed.getId();
        CountDownLatch loaded = new CountDownLatch(2);
        CountDownLatch continueToFlush = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<Boolean> a = workers.submit(() -> updateProduct(productId, new BigDecimal("11.00"), loaded, continueToFlush));
            Future<Boolean> b = workers.submit(() -> updateProduct(productId, new BigDecimal("12.00"), loaded, continueToFlush));
            loaded.await();
            continueToFlush.countDown();
            assertEquals(1, (a.get() ? 1 : 0) + (b.get() ? 1 : 0));
        }

        Product persisted = productRepository.findById(productId).orElseThrow();
        assertEquals(1L, persisted.getVersion());
        List<OutboxMessage> messages = repository.findTop100ByStatusOrderByCreatedAtAsc(
                dev.erkut.productservice.outbox.domain.OutboxStatus.PENDING).stream()
                .filter(message -> message.getAggregateId().equals(productId)).toList();
        assertEquals(1, messages.size());
        ProductUpdatedEvent event = new tools.jackson.databind.json.JsonMapper()
                .treeToValue(messages.getFirst().getPayload(), ProductUpdatedEvent.class);
        assertEquals(1L, event.version());
    }

    private List<ClaimedOutboxMessage> claimWhenReleased(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return outboxService.claimMessages(1, 180);
    }

    private OutboxMessage create(UUID aggregateId, Instant createdAt) {
        outboxService.createProductCreatedEvent(new ProductCreatedEvent(
                aggregateId, "Product", new BigDecimal("10.00"), ProductStatus.ACTIVE,
                createdAt, createdAt, 0), createdAt);
        return repository.findTop100ByStatusOrderByCreatedAtAsc(
                dev.erkut.productservice.outbox.domain.OutboxStatus.PENDING).stream()
                .filter(message -> message.getAggregateId().equals(aggregateId))
                .max(java.util.Comparator.comparing(OutboxMessage::getCreatedAt))
                .orElseThrow();
    }

    private boolean updateProduct(UUID productId, BigDecimal price, CountDownLatch loaded, CountDownLatch continueToFlush) {
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                Product product = productRepository.findById(productId).orElseThrow();
                product.changeProductPrice(price, Instant.parse("2026-01-02T00:00:00Z"));
                loaded.countDown();
                try {
                    continueToFlush.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                productRepository.saveAndFlush(product);
                outboxService.createProductUpdatedEvent(new ProductUpdatedEvent(
                        productId, product.getName(), product.getPrice(), product.getStatus(),
                        product.getCreatedAt(), product.getUpdatedAt(), product.getVersion()), product.getUpdatedAt());
                return null;
            });
            return true;
        } catch (RuntimeException optimisticConflict) {
            return false;
        }
    }
}
