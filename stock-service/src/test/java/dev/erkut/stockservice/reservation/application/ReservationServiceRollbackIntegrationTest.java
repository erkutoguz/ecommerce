package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.outbox.application.OutboxService;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class ReservationServiceRollbackIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxService outboxService;

    @Test
    void unexpectedOutboxFailureRollsBackInboxStockAndReservation() {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO stock_items (
                    product_id, on_hand_quantity, reserved_quantity, active, updated_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, productId, 10, 0, true,
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
        doThrow(new RuntimeException("outbox persistence failure"))
                .when(outboxService).createStockReservedEvent(any(), any());

        assertThrows(RuntimeException.class, () -> reservationService.handleReserveStock(
                new MessageEnvelope(messageId, "RESERVE_STOCK_COMMAND", CREATED_AT,
                        new tools.jackson.databind.json.JsonMapper().createObjectNode()),
                new ReserveStockCommand(orderId,
                        List.of(new ReserveStockCommand.ReserveStockItem(productId, 2)))
        ));

        assertFalse(inboxRepository.existsById(messageId));
        assertTrue(reservationRepository.findById(orderId).isEmpty());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM stock_items WHERE product_id = ?",
                Integer.class,
                productId
        ));
    }
}
