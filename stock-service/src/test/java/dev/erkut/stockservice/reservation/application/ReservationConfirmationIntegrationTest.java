package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import dev.erkut.stockservice.outbox.domain.OutboxMessageType;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.domain.ReservationStatus;
import dev.erkut.stockservice.reservation.domain.exception.ReservationNotFoundException;
import dev.erkut.stockservice.reservation.domain.exception.ReservationStatusException;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
class ReservationConfirmationIntegrationTest {

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
    private OutboxMessageRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM outbox_messages");
        jdbcTemplate.update("DELETE FROM reservation_items");
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM stock_items");
    }

    @Test
    void reservedReservationIsConfirmedWithoutChangingStockAndCreatesEventOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 2);

        confirm(orderId, UUID.randomUUID());

        assertEquals(2, reservedQuantity(productId));
        assertEquals(ReservationStatus.CONFIRMED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());

        OutboxMessage confirmation = outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType() == OutboxMessageType.STOCK_RESERVATION_CONFIRMED_EVENT)
                .findFirst()
                .orElseThrow();
        assertEquals(orderId, confirmation.getAggregateId());
        assertEquals(orderId.toString(), confirmation.getPayload().get("orderId").asText());
        assertEquals(2, outboxRepository.count());
        assertEquals(2, inboxRepository.count());
    }

    @Test
    void duplicateConfirmationMessageDoesNotCreateSecondMutationOrOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 2);
        MessageEnvelope envelope = confirmationEnvelope(messageId, orderId);
        ConfirmStockReservationCommand command = new ConfirmStockReservationCommand(orderId);

        reservationService.handleConfirmStockReservationCommand(envelope, command);
        reservationService.handleConfirmStockReservationCommand(envelope, command);

        assertEquals(2, reservedQuantity(productId));
        assertEquals(ReservationStatus.CONFIRMED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, inboxRepository.count());
        assertEquals(2, outboxRepository.count());
    }

    @Test
    void confirmationFromNonReservedStateIsRejectedAndSecondMessageRollsBack() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 2);
        confirm(orderId, UUID.randomUUID());

        assertThrows(ReservationStatusException.class,
                () -> confirm(orderId, UUID.randomUUID()));

        assertEquals(ReservationStatus.CONFIRMED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, inboxRepository.count());
        assertEquals(2, outboxRepository.count());
    }

    @Test
    void missingReservationRollsBackInboxClaim() {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        assertThrows(ReservationNotFoundException.class,
                () -> confirm(orderId, messageId));

        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, reservationRepository.count());
        assertEquals(0, outboxRepository.count());
    }

    private void reserve(UUID orderId, UUID productId, int quantity) {
        reservationService.handleReserveStock(
                envelope(UUID.randomUUID(), "RESERVE_STOCK_COMMAND",
                        new ReserveStockCommand(orderId, List.of(
                                new ReserveStockCommand.ReserveStockItem(productId, quantity)
                        ))),
                new ReserveStockCommand(orderId, List.of(
                        new ReserveStockCommand.ReserveStockItem(productId, quantity)
                ))
        );
    }

    private void confirm(UUID orderId, UUID messageId) {
        reservationService.handleConfirmStockReservationCommand(
                confirmationEnvelope(messageId, orderId),
                new ConfirmStockReservationCommand(orderId)
        );
    }

    private void insertStock(UUID productId, int onHand, int reserved) {
        jdbcTemplate.update("""
                INSERT INTO stock_items (
                    product_id, on_hand_quantity, reserved_quantity, active, updated_at, created_at
                ) VALUES (?, ?, ?, true, ?, ?)
                """, productId, onHand, reserved,
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private int reservedQuantity(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM stock_items WHERE product_id = ?",
                Integer.class,
                productId
        );
    }

    private static MessageEnvelope confirmationEnvelope(UUID messageId, UUID orderId) {
        return envelope(messageId, "CONFIRM_STOCK_RESERVATION_COMMAND",
                new ConfirmStockReservationCommand(orderId));
    }

    private static MessageEnvelope envelope(UUID messageId, String messageType, Object payload) {
        return new MessageEnvelope(
                messageId,
                messageType,
                CREATED_AT,
                new tools.jackson.databind.json.JsonMapper().valueToTree(payload)
        );
    }
}
