package dev.erkut.stockservice.stock.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.event.ProductCreatedEvent;
import dev.erkut.stockservice.stock.persistence.StockItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
class StockServiceRollbackIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private StockService stockService;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @MockitoBean
    private StockItemRepository stockItemRepository;

    @Test
    void stockItemFailureRollsBackInboxClaim() {
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(stockItemRepository.save(any()))
                .thenThrow(new RuntimeException("stock persistence failure"));

        assertThrows(RuntimeException.class, () -> stockService.handleProductCreated(
                new MessageEnvelope(messageId, "PRODUCT_CREATED", Instant.now(),
                        new tools.jackson.databind.json.JsonMapper()
                                .valueToTree(new ProductCreatedEvent(productId))),
                new ProductCreatedEvent(productId)));

        assertEquals(false, inboxRepository.existsById(messageId));
    }
}
