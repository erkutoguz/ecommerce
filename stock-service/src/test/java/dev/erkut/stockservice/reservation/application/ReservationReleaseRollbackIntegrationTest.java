package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ReleaseStockReservationCommand;
import dev.erkut.stockservice.outbox.application.OutboxService;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.domain.Reservation;
import dev.erkut.stockservice.reservation.domain.ReservationStatus;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Testcontainers
class ReservationReleaseRollbackIntegrationTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T10:00:00Z");

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private InboxMessageRepository inboxRepository;

    @Autowired
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxService outboxService;

    @Test
    void outboxFailureRollsBackReleaseQuantityReservationAndInboxClaim() {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO stock_items (
                    product_id, on_hand_quantity, reserved_quantity, active, updated_at, created_at
                ) VALUES (?, 10, 2, true, ?, ?)
                """, productId, Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
        Reservation reservation = Reservation.create(orderId, CREATED_AT);
        reservation.addItem(productId, 2);
        reservationRepository.saveAndFlush(reservation);

        doThrow(new RuntimeException("outbox persistence failure"))
                .when(outboxService)
                .createStockReservationReleasedEvent(any(), any());

        assertThrows(RuntimeException.class, () -> reservationService
                .handleReleaseStockReservationCommand(
                        new MessageEnvelope(
                                messageId,
                                "RELEASE_STOCK_RESERVATION_COMMAND",
                                CREATED_AT,
                                new tools.jackson.databind.json.JsonMapper().createObjectNode()
                        ),
                        new ReleaseStockReservationCommand(orderId)
                ));

        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(ReservationStatus.RESERVED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(10, quantity(productId, "on_hand_quantity"));
        assertEquals(2, quantity(productId, "reserved_quantity"));
        assertEquals(0, outboxRepository.count());
    }

    private int quantity(UUID productId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM stock_items WHERE product_id = ?",
                Integer.class,
                productId
        );
    }
}
