package dev.erkut.orderworkflowservice.outbox.application;

import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxStatus;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = {"spring.jpa.properties.hibernate.type.json_format_mapper=jackson3", "logging.level.root=ERROR"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
@Testcontainers
class OutboxClaimConcurrencyIntegrationTest {
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired OutboxMessageRepository repository;
    @Autowired PlatformTransactionManager transactionManager;
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @BeforeEach
    void cleanOutbox() {
        repository.deleteAll();
    }

    @Test
    void concurrentWorkersCannotOwnTheSameActiveClaim() throws Exception {
        create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService workers = Executors.newFixedThreadPool(2)) {
            Future<List<Claim>> a = workers.submit(() -> claimTogether(ready, start));
            Future<List<Claim>> b = workers.submit(() -> claimTogether(ready, start));
            ready.await();
            start.countDown();
            assertEquals(1, a.get().size() + b.get().size());
        }
        assertEquals(1, repository.countByStatus(OutboxStatus.IN_PROGRESS));
    }

    @Test
    void differentAggregatesProgressWhileSameAggregateRemainsOrdered() {
        UUID aggregate = UUID.randomUUID();
        OutboxMessage head = create(aggregate, Instant.parse("2026-01-01T00:00:00Z"));
        OutboxMessage later = create(aggregate, Instant.parse("2026-01-01T00:00:01Z"));
        OutboxMessage independent = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:02Z"));

        List<Claim> claims = claim(10, 180);
        assertEquals(2, claims.size());
        assertTrue(claims.stream().anyMatch(c -> c.id().equals(head.getId())));
        assertTrue(claims.stream().noneMatch(c -> c.id().equals(later.getId())));
        assertTrue(claims.stream().anyMatch(c -> c.id().equals(independent.getId())));
    }

    @Test
    void expiredClaimCanBeReclaimedAndStaleTokenCannotComplete() {
        OutboxMessage message = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        Claim oldClaim = claim(1, 0).getFirst();
        Claim newClaim = claim(1, 180).getFirst();

        assertEquals(message.getId(), newClaim.id());
        assertNotEquals(oldClaim.token(), newClaim.token());
        assertEquals(0, complete(message.getId(), oldClaim.token()));
        assertEquals(1, complete(message.getId(), newClaim.token()));
        assertEquals(OutboxStatus.PUBLISHED, repository.findById(message.getId()).orElseThrow().getStatus());
    }

    @Test
    void rolledBackClaimDoesNotBecomeCommittedOwnership() {
        OutboxMessage message = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.execute(status -> {
            assertEquals(1, repository.claimEligibleMessages(1, 180).size());
            status.setRollbackOnly();
            return null;
        });
        assertEquals(OutboxStatus.PENDING, repository.findById(message.getId()).orElseThrow().getStatus());
    }

    @Test
    void failedMessageCanBeRescheduledAndRetriedWithTheSameId() {
        OutboxMessage message = create(UUID.randomUUID(), Instant.parse("2026-01-01T00:00:00Z"));
        Claim first = claim(1, 180).getFirst();
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        int scheduled = template.execute(status -> repository.rescheduleClaimedMessage(
                message.getId(), first.token(), Instant.now().minusSeconds(1)));
        assertEquals(1, scheduled);

        Claim retry = claim(1, 180).getFirst();
        assertEquals(message.getId(), retry.id());
        assertNotEquals(first.token(), retry.token());
    }

    private List<Claim> claimTogether(CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        start.await();
        return claim(1, 180);
    }

    private List<Claim> claim(int batchSize, long leaseSeconds) {
        return transaction(() -> repository.claimEligibleMessages(batchSize, leaseSeconds).stream()
                .map(row -> new Claim((UUID) row[0], (UUID) row[1])).toList());
    }

    private int complete(UUID id, UUID token) {
        return transaction(() -> repository.markClaimedMessagePublished(id, token, Instant.now()));
    }

    private <T> T transaction(java.util.function.Supplier<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private OutboxMessage create(UUID aggregateId, Instant createdAt) {
        return repository.saveAndFlush(OutboxMessage.create(aggregateId, OutboxMessageType.values()[0],
                jsonMapper.createObjectNode().put("test", true), createdAt));
    }

    private record Claim(UUID id, UUID token) {}
}
