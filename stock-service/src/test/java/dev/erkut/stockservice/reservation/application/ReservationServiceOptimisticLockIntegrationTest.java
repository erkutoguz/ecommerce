package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import dev.erkut.stockservice.stock.application.StockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Testcontainers
class ReservationServiceOptimisticLockIntegrationTest {

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

    @MockitoSpyBean
    private StockService stockService;

    @BeforeEach
    void clearData() {
        jdbcTemplate.update("DELETE FROM outbox_messages");
        jdbcTemplate.update("DELETE FROM reservation_items");
        jdbcTemplate.update("DELETE FROM reservations");
        jdbcTemplate.update("DELETE FROM inbox_messages");
        jdbcTemplate.update("DELETE FROM stock_items");
    }

    @Test
    void concurrentReservationsOnTheSameVersionAllowOneCommitAndRollbackTheLoser()
            throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        UUID messageA = UUID.randomUUID();
        UUID messageB = UUID.randomUUID();
        insertStock(productId, 8);

        CountDownLatch bothTransactionsReadTheSameStock = new CountDownLatch(2);
        doAnswer(invocation -> {
            Optional<?> result = (Optional<?>) invocation.callRealMethod();
            bothTransactionsReadTheSameStock.countDown();
            if (!bothTransactionsReadTheSameStock.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("Both transactions did not read the stock item");
            }
            return result;
        }).when(stockService).findStockItemById(productId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ReservationAttempt> first = executor.submit(() -> reserve(
                    orderA,
                    messageA,
                    productId
            ));
            Future<ReservationAttempt> second = executor.submit(() -> reserve(
                    orderB,
                    messageB,
                    productId
            ));

            ReservationOutcome firstOutcome = outcome(first, orderA, messageA);
            ReservationOutcome secondOutcome = outcome(second, orderB, messageB);

            assertEquals(1, List.of(firstOutcome, secondOutcome).stream()
                    .filter(ReservationOutcome::succeeded)
                    .count());
            assertEquals(1, List.of(firstOutcome, secondOutcome).stream()
                    .filter(outcome -> !outcome.succeeded())
                    .count());

            ReservationOutcome winner = firstOutcome.succeeded() ? firstOutcome : secondOutcome;
            ReservationOutcome loser = firstOutcome.succeeded() ? secondOutcome : firstOutcome;

            assertTrue(isOptimisticLockFailure(loser.failure()));
            assertEquals(6, reservedQuantity(productId));
            assertTrue(reservationRepository.existsById(winner.orderId()));
            assertFalse(reservationRepository.existsById(loser.orderId()));
            assertTrue(inboxRepository.existsById(winner.messageId()));
            assertFalse(inboxRepository.existsById(loser.messageId()));
            assertEquals(1, reservationRepository.count());
            assertEquals(1, inboxRepository.count());
            assertEquals(1, outboxRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private ReservationAttempt reserve(UUID orderId, UUID messageId, UUID productId) {
        reservationService.handleReserveStock(
                envelope(messageId),
                new ReserveStockCommand(
                        orderId,
                        List.of(new ReserveStockCommand.ReserveStockItem(productId, 6))
                )
        );
        return new ReservationAttempt(orderId, messageId);
    }

    private ReservationOutcome outcome(
            Future<ReservationAttempt> future,
            UUID expectedOrderId,
            UUID expectedMessageId
    ) throws Exception {
        try {
            ReservationAttempt attempt = future.get(15, TimeUnit.SECONDS);
            return new ReservationOutcome(attempt.orderId(), attempt.messageId(), null);
        } catch (ExecutionException exception) {
            return new ReservationOutcome(expectedOrderId, expectedMessageId, exception.getCause());
        }
    }

    private static boolean isOptimisticLockFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof org.springframework.dao.OptimisticLockingFailureException
                    || current instanceof jakarta.persistence.OptimisticLockException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private void insertStock(UUID productId, int onHandQuantity) {
        jdbcTemplate.update("""
                INSERT INTO stock_items (
                    product_id, on_hand_quantity, reserved_quantity, active, updated_at, created_at
                ) VALUES (?, ?, 0, true, ?, ?)
                """, productId, onHandQuantity,
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private int reservedQuantity(UUID productId) {
        return jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM stock_items WHERE product_id = ?",
                Integer.class,
                productId
        );
    }

    private static MessageEnvelope envelope(UUID messageId) {
        return new MessageEnvelope(
                messageId,
                "RESERVE_STOCK_COMMAND",
                CREATED_AT,
                new tools.jackson.databind.json.JsonMapper().createObjectNode()
        );
    }

    private record ReservationAttempt(UUID orderId, UUID messageId) {}

    private record ReservationOutcome(UUID orderId, UUID messageId, Throwable failure) {
        boolean succeeded() {
            return failure == null;
        }
    }
}
