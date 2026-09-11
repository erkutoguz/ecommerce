package dev.erkut.stockservice.reservation.application;

import dev.erkut.stockservice.inbox.persistence.InboxMessageRepository;
import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.message.command.ReleaseStockReservationCommand;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.domain.ReservationStatus;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@Testcontainers
class ReservationConfirmReleaseConcurrencyIntegrationTest {

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
    void concurrentConfirmAndReleaseAllowOnlyOneConsistentTerminalTransition()
            throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        insertStock(productId, 10, 0);
        reserve(orderId, productId, 3);

        CountDownLatch bothOperationsReadReservedReservation = new CountDownLatch(2);
        blockUntilBothOperationsReadReservation(bothOperationsReadReservedReservation);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<OperationOutcome> confirm = executor.submit(() -> invokeConfirm(orderId));
            Future<OperationOutcome> release = executor.submit(() -> invokeRelease(orderId));

            OperationOutcome confirmOutcome = confirm.get(15, TimeUnit.SECONDS);
            OperationOutcome releaseOutcome = release.get(15, TimeUnit.SECONDS);

            assertEquals(1, List.of(confirmOutcome, releaseOutcome).stream()
                    .filter(OperationOutcome::succeeded)
                    .count());
            assertEquals(1, List.of(confirmOutcome, releaseOutcome).stream()
                    .filter(outcome -> !outcome.succeeded())
                    .count());

            ReservationStatus finalStatus = reservationRepository.findById(orderId)
                    .orElseThrow()
                    .getStatus();
            if (confirmOutcome.succeeded()) {
                assertEquals(ReservationStatus.CONFIRMED, finalStatus);
                assertStock(productId, 7, 0);
                assertTrue(isOptimisticLockFailure(releaseOutcome.failure()));
            } else {
                assertEquals(ReservationStatus.RELEASED, finalStatus);
                assertStock(productId, 10, 0);
                assertTrue(isOptimisticLockFailure(confirmOutcome.failure()));
            }

            assertEquals(2, inboxRepository.count());
            assertEquals(2, outboxRepository.count());
        } finally {
            executor.shutdownNow();
        }
    }

    private void blockUntilBothOperationsReadReservation(CountDownLatch latch) {
        doAnswer(invocation -> {
            awaitBothOperations(latch);
            return invocation.callRealMethod();
        }).when(stockService).handleProductConfirm(anyList());

        doAnswer(invocation -> {
            awaitBothOperations(latch);
            return invocation.callRealMethod();
        }).when(stockService).handleProductRelease(anyList());
    }

    private void awaitBothOperations(CountDownLatch latch) throws InterruptedException {
        latch.countDown();
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Both operations did not read the reserved reservation");
        }
    }

    private OperationOutcome invokeConfirm(UUID orderId) {
        UUID messageId = UUID.randomUUID();
        try {
            reservationService.handleConfirmStockReservationCommand(
                    envelope(messageId, "CONFIRM_STOCK_RESERVATION_COMMAND"),
                    new ConfirmStockReservationCommand(orderId)
            );
            return new OperationOutcome(true, null);
        } catch (Throwable failure) {
            return new OperationOutcome(false, failure);
        }
    }

    private OperationOutcome invokeRelease(UUID orderId) {
        UUID messageId = UUID.randomUUID();
        try {
            reservationService.handleReleaseStockReservationCommand(
                    envelope(messageId, "RELEASE_STOCK_RESERVATION_COMMAND"),
                    new ReleaseStockReservationCommand(orderId)
            );
            return new OperationOutcome(true, null);
        } catch (Throwable failure) {
            return new OperationOutcome(false, failure);
        }
    }

    private void reserve(UUID orderId, UUID productId, int quantity) {
        List<ReserveStockCommand.ReserveStockItem> items = List.of(
                new ReserveStockCommand.ReserveStockItem(productId, quantity)
        );
        reservationService.handleReserveStock(
                envelope(UUID.randomUUID(), "RESERVE_STOCK_COMMAND"),
                new ReserveStockCommand(orderId, items)
        );
    }

    private MessageEnvelope envelope(UUID messageId, String messageType) {
        return new MessageEnvelope(
                messageId,
                messageType,
                CREATED_AT,
                new tools.jackson.databind.json.JsonMapper().createObjectNode()
        );
    }

    private void insertStock(UUID productId, int onHandQuantity, int reservedQuantity) {
        jdbcTemplate.update("""
                INSERT INTO stock_items (
                    product_id, on_hand_quantity, reserved_quantity, active, updated_at, created_at
                ) VALUES (?, ?, ?, true, ?, ?)
                """, productId, onHandQuantity, reservedQuantity,
                Timestamp.from(CREATED_AT), Timestamp.from(CREATED_AT));
    }

    private void assertStock(UUID productId, int expectedOnHand, int expectedReserved) {
        assertEquals(expectedOnHand, jdbcTemplate.queryForObject(
                "SELECT on_hand_quantity FROM stock_items WHERE product_id = ?",
                Integer.class,
                productId
        ));
        assertEquals(expectedReserved, jdbcTemplate.queryForObject(
                "SELECT reserved_quantity FROM stock_items WHERE product_id = ?",
                Integer.class,
                productId
        ));
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

    private record OperationOutcome(boolean succeeded, Throwable failure) {}
}
