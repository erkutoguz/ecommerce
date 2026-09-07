package dev.erkut.stockservice.stock.application;

import dev.erkut.stockservice.inbox.domain.InboxMessage;
import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.stock.domain.StockItem;
import dev.erkut.stockservice.stock.persistence.StockItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@Testcontainers
class StockServiceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private StockService stockService;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Test
    void productCreatedCreatesInboxAndZeroedStockItem() {
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        stockService.handleProductCreated(envelope(messageId, productId),
                new ProductCreatedEvent(productId));

        InboxMessage inbox = inboxRepository.findById(messageId).orElseThrow();
        StockItem item = stockItemRepository.findById(productId).orElseThrow();

        assertEquals(messageId, inbox.getMessageId());
        assertEquals(productId, inbox.getAggregateId());
        assertEquals("PRODUCT_CREATED", inbox.getMessageType());
        assertEquals(productId, item.getProductId());
        assertEquals(0, item.getOnHandQuantity());
        assertEquals(0, item.getReservedQuantity());
    }

    @Test
    void sequentialDuplicateCreatesOnlyOneInboxAndStockItem() {
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        MessageEnvelope envelope = envelope(messageId, productId);
        ProductCreatedEvent event = new ProductCreatedEvent(productId);

        stockService.handleProductCreated(envelope, event);
        assertDoesNotThrow(() -> stockService.handleProductCreated(envelope, event));

        assertEquals(1, inboxRepository.findById(messageId).stream().count());
        assertEquals(1, stockItemRepository.findById(productId).stream().count());
    }

    @Test
    void concurrentDuplicateClaimsCreateOneInboxAndOneStockItem() throws Exception {
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        MessageEnvelope envelope = envelope(messageId, productId);
        ProductCreatedEvent event = new ProductCreatedEvent(productId);
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var first = executor.submit(() -> {
                barrier.await();
                stockService.handleProductCreated(envelope, event);
                return null;
            });
            var second = executor.submit(() -> {
                barrier.await();
                stockService.handleProductCreated(envelope, event);
                return null;
            });

            assertDoesNotThrow(() -> {
                first.get();
            });
            assertDoesNotThrow(() -> {
                second.get();
            });
        } finally {
            executor.shutdownNow();
        }

        assertNotNull(inboxRepository.findById(messageId).orElse(null));
        assertNotNull(stockItemRepository.findById(productId).orElse(null));
    }

    private static MessageEnvelope envelope(UUID messageId, UUID productId) {
        return new MessageEnvelope(
                messageId,
                "PRODUCT_CREATED",
                Instant.parse("2026-01-01T10:00:00Z"),
                new JsonMapper().valueToTree(new ProductCreatedEvent(productId))
        );
    }
}
