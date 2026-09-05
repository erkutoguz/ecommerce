package dev.erkut.orderservice.outbox.persistence;

import dev.erkut.orderservice.outbox.domain.OutboxMessage;
import dev.erkut.orderservice.outbox.domain.OutboxMessageType;
import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@TestPropertySource(properties = "spring.jpa.properties.hibernate.type.json_format_mapper=jackson3")
class OutboxMessagePersistenceTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static final UUID ORDER_ID = UUID.fromString("80000000-0000-0000-0000-000000000001");
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final JsonNode PAYLOAD = new JsonMapper().readTree(
            "{\"orderId\":\"80000000-0000-0000-0000-000000000001\"}"
    );

    @Autowired
    private OutboxMessageRepository outboxMessageRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void save_shouldPersistOutboxMessageWithJsonbPayload() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        UUID messageId = transactionTemplate.execute(status -> {
            OutboxMessage message = OutboxMessage.create(
                    ORDER_ID,
                    OutboxMessageType.ORDER_CHECKOUT_STARTED,
                    PAYLOAD,
                    CREATED_AT
            );
            OutboxMessage saved = outboxMessageRepository.saveAndFlush(message);
            entityManager.clear();
            return saved.getId();
        });

        OutboxMessage reloaded = transactionTemplate.execute(status ->
                outboxMessageRepository.findById(messageId).orElseThrow()
        );

        assertEquals(messageId, reloaded.getId());
        assertEquals(ORDER_ID, reloaded.getAggregateId());
        assertEquals(PAYLOAD, reloaded.getPayload());
        assertEquals(OutboxStatus.PENDING, reloaded.getStatus());
        assertEquals(OutboxMessageType.ORDER_CHECKOUT_STARTED, reloaded.getMessageType());
        assertEquals(CREATED_AT, reloaded.getCreatedAt());
        assertNull(reloaded.getPublishedAt());
    }
}
