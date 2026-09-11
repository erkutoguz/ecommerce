package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.message.command.ReleaseStockReservationCommand;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import dev.erkut.stockservice.outbox.domain.OutboxMessageType;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.domain.Reservation;
import dev.erkut.stockservice.reservation.domain.ReservationStatus;
import dev.erkut.stockservice.reservation.domain.exception.ReservationStatusException;
import dev.erkut.stockservice.stock.domain.exception.InsufficientStockException;
import dev.erkut.stockservice.stock.domain.exception.StockItemNotFoundException;
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
class ReservationReleaseIntegrationTest {

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
    void reservedReservationIsReleasedWithoutChangingOnHandAndCreatesEventOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 3);

        release(orderId, UUID.randomUUID());

        assertStock(productId, 10, 0, 10);
        assertEquals(ReservationStatus.RELEASED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, reservationItemCount(orderId));

        OutboxMessage releasedEvent = outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType()
                        == OutboxMessageType.STOCK_RESERVATION_RELEASED_EVENT)
                .findFirst()
                .orElseThrow();
        assertEquals(orderId, releasedEvent.getAggregateId());
        assertEquals(orderId.toString(), releasedEvent.getPayload().get("orderId").asText());
        assertEquals(2, outboxRepository.count());
        assertEquals(2, inboxRepository.count());
    }

    @Test
    void multiItemReleaseReturnsReservedQuantityForEveryItem() {
        UUID orderId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        insertStock(firstProductId, 10, 0);
        insertStock(secondProductId, 20, 0);
        reserve(orderId,
                new ReservationItemSeed(firstProductId, 2),
                new ReservationItemSeed(secondProductId, 5));

        release(orderId, UUID.randomUUID());

        assertStock(firstProductId, 10, 0, 10);
        assertStock(secondProductId, 20, 0, 20);
        assertEquals(ReservationStatus.RELEASED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, reservationItemCount(orderId));
        assertEquals(1, outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType()
                        == OutboxMessageType.STOCK_RESERVATION_RELEASED_EVENT)
                .count());
    }

    @Test
    void duplicateReleaseWithSameMessageIdDoesNotReduceQuantityTwice() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 3);
        MessageEnvelope envelope = releaseEnvelope(messageId, orderId);
        ReleaseStockReservationCommand command = new ReleaseStockReservationCommand(orderId);

        reservationService.handleReleaseStockReservationCommand(envelope, command);
        reservationService.handleReleaseStockReservationCommand(envelope, command);

        assertStock(productId, 10, 0, 10);
        assertEquals(ReservationStatus.RELEASED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, inboxRepository.count());
        assertEquals(2, outboxRepository.count());
    }

    @Test
    void releaseAfterConfirmationDoesNotMutateStockOrCreateReleaseEvent() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 3);
        reservationService.handleConfirmStockReservationCommand(
                confirmationEnvelope(UUID.randomUUID(), orderId),
                new ConfirmStockReservationCommand(orderId)
        );

        UUID releaseMessageId = UUID.randomUUID();
        assertThrows(ReservationStatusException.class,
                () -> release(orderId, releaseMessageId));

        assertStock(productId, 7, 0, 7);
        assertEquals(ReservationStatus.CONFIRMED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(1, reservationItemCount(orderId));
        assertFalse(inboxRepository.existsById(releaseMessageId));
        assertEquals(2, outboxRepository.count());
        assertEquals(0, outboxRepository.findAll().stream()
                .filter(message -> message.getMessageType()
                        == OutboxMessageType.STOCK_RESERVATION_RELEASED_EVENT)
                .count());
    }

    @Test
    void releaseAfterReleaseWithDifferentMessageIdDoesNotMutateAgain() {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 3);
        release(orderId, UUID.randomUUID());

        UUID duplicateMessageId = UUID.randomUUID();
        assertThrows(ReservationStatusException.class,
                () -> release(orderId, duplicateMessageId));

        assertStock(productId, 10, 0, 10);
        assertEquals(ReservationStatus.RELEASED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertFalse(inboxRepository.existsById(duplicateMessageId));
        assertEquals(2, outboxRepository.count());
    }

    @Test
    void releaseValidationFailureDoesNotMutateAnyStockItem() {
        UUID orderId = UUID.randomUUID();
        UUID validProductId = UUID.randomUUID();
        UUID missingProductId = UUID.randomUUID();
        insertStock(validProductId, 10, 2);
        persistReservation(orderId,
                new ReservationItemSeed(validProductId, 2),
                new ReservationItemSeed(missingProductId, 1));

        UUID messageId = UUID.randomUUID();
        assertThrows(StockItemNotFoundException.class,
                () -> release(orderId, messageId));

        assertStock(validProductId, 10, 2, 8);
        assertEquals(ReservationStatus.RESERVED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertEquals(2, reservationItemCount(orderId));
        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, outboxRepository.count());
    }

    @Test
    void releaseInsufficientReservedQuantityDoesNotPartiallyMutate() {
        UUID orderId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        insertStock(firstProductId, 10, 2);
        insertStock(secondProductId, 20, 4);
        persistReservation(orderId,
                new ReservationItemSeed(firstProductId, 2),
                new ReservationItemSeed(secondProductId, 5));

        UUID messageId = UUID.randomUUID();
        assertThrows(InsufficientStockException.class,
                () -> release(orderId, messageId));

        assertStock(firstProductId, 10, 2, 8);
        assertStock(secondProductId, 20, 4, 16);
        assertEquals(ReservationStatus.RESERVED,
                reservationRepository.findById(orderId).orElseThrow().getStatus());
        assertFalse(inboxRepository.existsById(messageId));
        assertEquals(0, outboxRepository.count());
    }

    private void reserve(UUID orderId, UUID productId, int quantity) {
        reserve(orderId, new ReservationItemSeed(productId, quantity));
    }

    private void reserve(UUID orderId, ReservationItemSeed... items) {
        List<ReserveStockCommand.ReserveStockItem> commandItems =
                java.util.Arrays.stream(items)
                        .map(item -> new ReserveStockCommand.ReserveStockItem(
                                item.productId(), item.quantity()))
                        .toList();
        reservationService.handleReserveStock(
                envelope(UUID.randomUUID(), "RESERVE_STOCK_COMMAND",
                        new ReserveStockCommand(orderId, commandItems)),
                new ReserveStockCommand(orderId, commandItems)
        );
    }

    private void release(UUID orderId, UUID messageId) {
        reservationService.handleReleaseStockReservationCommand(
                releaseEnvelope(messageId, orderId),
                new ReleaseStockReservationCommand(orderId)
        );
    }

    private void persistReservation(UUID orderId, ReservationItemSeed... items) {
        Reservation reservation = Reservation.create(orderId, CREATED_AT);
        for (ReservationItemSeed item : items) {
            reservation.addItem(item.productId(), item.quantity());
        }
        reservationRepository.saveAndFlush(reservation);
    }

    private void insertStock(UUID productId, int onHand, int reserved) {
        jdbcTemplate.update("""
                INSERT INTO stock_items (
                    product_id, on_hand_quantity, reserved_quantity, active, updated_at, created_at
                ) VALUES (?, ?, ?, true, ?, ?)
                """, productId, onHand, reserved,
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private void assertStock(UUID productId, int expectedOnHand, int expectedReserved, int expectedAvailable) {
        assertEquals(expectedOnHand, quantity(productId, "on_hand_quantity"));
        assertEquals(expectedReserved, quantity(productId, "reserved_quantity"));
        assertEquals(expectedAvailable,
                quantity(productId, "on_hand_quantity") - quantity(productId, "reserved_quantity"));
    }

    private int quantity(UUID productId, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT " + column + " FROM stock_items WHERE product_id = ?",
                Integer.class,
                productId
        );
    }

    private int reservationItemCount(UUID orderId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reservation_items WHERE order_id = ?",
                Integer.class,
                orderId
        );
    }

    private static MessageEnvelope confirmationEnvelope(UUID messageId, UUID orderId) {
        return envelope(messageId, "CONFIRM_STOCK_RESERVATION_COMMAND",
                new ConfirmStockReservationCommand(orderId));
    }

    private static MessageEnvelope releaseEnvelope(UUID messageId, UUID orderId) {
        return envelope(messageId, "RELEASE_STOCK_RESERVATION_COMMAND",
                new ReleaseStockReservationCommand(orderId));
    }

    private static MessageEnvelope envelope(UUID messageId, String messageType, Object payload) {
        return new MessageEnvelope(
                messageId,
                messageType,
                CREATED_AT,
                new tools.jackson.databind.json.JsonMapper().valueToTree(payload)
        );
    }

    private record ReservationItemSeed(UUID productId, int quantity) {}
}
