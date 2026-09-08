package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.domain.InboxMessage;
import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import dev.erkut.stockservice.outbox.domain.OutboxMessageType;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.domain.Reservation;
import dev.erkut.stockservice.reservation.domain.StockReservationFailureReason;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import dev.erkut.stockservice.stock.persistence.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
class ReservationServiceIntegrationTest {

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
    void successfulReservationPersistsAllStateAndSuccessOutbox() {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        insertStock(firstProductId, 10, 0, true);
        insertStock(secondProductId, 5, 1, true);

        ReserveStockCommand command = command(orderId,
                new ReserveStockCommand.ReserveStockItem(firstProductId, 2),
                new ReserveStockCommand.ReserveStockItem(secondProductId, 3));

        reservationService.handleReserveStock(envelope(messageId), command);

        assertEquals(2, reservedQuantity(firstProductId));
        assertEquals(4, reservedQuantity(secondProductId));
        Reservation reservation = reservationRepository.findById(orderId).orElseThrow();
        assertEquals(orderId, reservation.getOrderId());
        assertEquals(2, reservationItemCount(orderId));

        OutboxMessage outbox = outboxFor(orderId);
        assertEquals(OutboxMessageType.STOCK_RESERVED_EVENT, outbox.getMessageType());
        assertEquals(orderId, outbox.getAggregateId());
        assertEquals("PENDING", outbox.getStatus().name());
        assertEquals(orderId.toString(), outbox.getPayload().get("orderId").asText());
        assertNotNull(inboxRepository.findById(messageId).orElse(null));
    }

    @ParameterizedTest
    @MethodSource("businessFailures")
    void businessFailureCommitsInboxAndFailureOutbox(
            StockReservationFailureReason reason,
            boolean itemExists,
            boolean active,
            int onHandQuantity,
            int requestedQuantity
    ) {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        if (itemExists) {
            insertStock(productId, onHandQuantity, 0, active);
        }

        reservationService.handleReserveStock(
                envelope(messageId),
                command(orderId, new ReserveStockCommand.ReserveStockItem(productId, requestedQuantity))
        );

        InboxMessage inbox = inboxRepository.findById(messageId).orElseThrow();
        assertEquals(orderId, inbox.getAggregateId());
        assertTrue(reservationRepository.findById(orderId).isEmpty());
        if (itemExists) {
            assertEquals(0, reservedQuantity(productId));
        }

        OutboxMessage outbox = outboxFor(orderId);
        assertEquals(OutboxMessageType.STOCK_RESERVATION_FAILED_EVENT, outbox.getMessageType());
        assertEquals(orderId, outbox.getAggregateId());
        assertEquals("PENDING", outbox.getStatus().name());
        JsonNode payload = outbox.getPayload();
        assertEquals(reason.name(), payload.get("reason").asText());
        assertEquals(productId.toString(), payload.get("productId").asText());
    }

    @Test
    void validationOfAllItemsPreventsPartialMutation() {
        UUID orderId = UUID.randomUUID();
        UUID firstProductId = UUID.randomUUID();
        UUID secondProductId = UUID.randomUUID();
        insertStock(firstProductId, 10, 0, true);
        insertStock(secondProductId, 1, 0, true);

        reservationService.handleReserveStock(
                envelope(UUID.randomUUID()),
                command(orderId,
                        new ReserveStockCommand.ReserveStockItem(firstProductId, 2),
                        new ReserveStockCommand.ReserveStockItem(secondProductId, 2))
        );

        assertEquals(0, reservedQuantity(firstProductId));
        assertEquals(0, reservedQuantity(secondProductId));
        assertTrue(reservationRepository.findById(orderId).isEmpty());
        OutboxMessage outbox = outboxFor(orderId);
        assertEquals(OutboxMessageType.STOCK_RESERVATION_FAILED_EVENT, outbox.getMessageType());
        assertEquals(StockReservationFailureReason.INSUFFICIENT_STOCK.name(),
                outbox.getPayload().get("reason").asText());
        assertEquals(secondProductId.toString(), outbox.getPayload().get("productId").asText());
    }

    @Test
    void duplicateMessageIdDoesNotProcessReservationTwice() {
        UUID orderId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        insertStock(productId, 10, 0, true);
        ReserveStockCommand command = command(orderId,
                new ReserveStockCommand.ReserveStockItem(productId, 2));
        MessageEnvelope envelope = envelope(messageId);

        reservationService.handleReserveStock(envelope, command);
        reservationService.handleReserveStock(envelope, command);

        assertEquals(2, reservedQuantity(productId));
        assertEquals(1, inboxRepository.findAll().size());
        assertEquals(1, reservationRepository.findAll().size());
        assertEquals(1, outboxRepository.findAll().size());
    }

    private static Stream<Arguments> businessFailures() {
        return Stream.of(
                Arguments.of(StockReservationFailureReason.ITEM_NOT_FOUND, false, true, 0, 1),
                Arguments.of(StockReservationFailureReason.ITEM_INACTIVE, true, false, 10, 1),
                Arguments.of(StockReservationFailureReason.INSUFFICIENT_STOCK, true, true, 1, 2)
        );
    }

    private void insertStock(UUID productId, int onHand, int reserved, boolean active) {
        jdbcTemplate.update("""
                INSERT INTO stock_items (
                    product_id, on_hand_quantity, reserved_quantity, active, updated_at, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, productId, onHand, reserved, active,
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private int reservedQuantity(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM stock_items WHERE product_id = ?",
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

    private OutboxMessage outboxFor(UUID orderId) {
        List<OutboxMessage> messages = outboxRepository.findAll().stream()
                .filter(message -> orderId.equals(message.getAggregateId()))
                .toList();
        assertEquals(1, messages.size());
        return messages.getFirst();
    }

    private static ReserveStockCommand command(
            UUID orderId,
            ReserveStockCommand.ReserveStockItem... items
    ) {
        return new ReserveStockCommand(orderId, List.of(items));
    }

    private static MessageEnvelope envelope(UUID messageId) {
        return new MessageEnvelope(
                messageId,
                "RESERVE_STOCK_COMMAND",
                CREATED_AT,
                new tools.jackson.databind.json.JsonMapper().createObjectNode()
        );
    }
}
