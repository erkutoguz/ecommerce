package dev.erkut.stockservice.stock.domain;

import dev.erkut.stockservice.stock.domain.exception.InactiveStockItemException;
import dev.erkut.stockservice.stock.domain.exception.InsufficientStockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockItemTest {

    @Test
    void positiveQuantityPassesValidation() {
        StockItem item = stockItem(10, 0);

        assertDoesNotThrow(() -> item.validateReservation(1));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveQuantityIsRejected(int quantity) {
        StockItem item = stockItem(10, 0);

        assertThrows(IllegalStateException.class,
                () -> item.validateReservation(quantity));
    }

    @Test
    void inactiveItemIsRejected() {
        StockItem item = stockItem(10, 0);
        item.deactivate();

        assertThrows(InactiveStockItemException.class,
                () -> item.validateReservation(1));
    }

    @Test
    void insufficientStockIsRejected() {
        StockItem item = stockItem(2, 0);

        assertThrows(InsufficientStockException.class,
                () -> item.validateReservation(3));
    }

    @Test
    void validReservationIncreasesReservedQuantity() {
        StockItem item = stockItem(10, 2);

        item.reserve(3);

        assertEquals(5, item.getReservedQuantity());
    }

    @Test
    void validConfirmationConsumesReservedAndOnHandQuantity() {
        StockItem item = stockItem(10, 3);

        item.confirm(3);

        assertEquals(7, item.getOnHandQuantity());
        assertEquals(0, item.getReservedQuantity());
    }

    @Test
    void validReleaseReturnsReservedQuantityWithoutChangingOnHandQuantity() {
        StockItem item = stockItem(10, 3);

        item.release(3);

        assertEquals(10, item.getOnHandQuantity());
        assertEquals(0, item.getReservedQuantity());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveConfirmationQuantityIsRejectedWithoutMutation(int quantity) {
        StockItem item = stockItem(10, 3);

        assertThrows(IllegalStateException.class, () -> item.confirm(quantity));

        assertEquals(10, item.getOnHandQuantity());
        assertEquals(3, item.getReservedQuantity());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonPositiveReleaseQuantityIsRejectedWithoutMutation(int quantity) {
        StockItem item = stockItem(10, 3);

        assertThrows(IllegalStateException.class, () -> item.release(quantity));

        assertEquals(10, item.getOnHandQuantity());
        assertEquals(3, item.getReservedQuantity());
    }

    @Test
    void confirmationAboveReservedQuantityIsRejectedWithoutMutation() {
        StockItem item = stockItem(10, 2);

        assertThrows(InsufficientStockException.class, () -> item.confirm(3));

        assertEquals(10, item.getOnHandQuantity());
        assertEquals(2, item.getReservedQuantity());
    }

    @Test
    void releaseAboveReservedQuantityIsRejectedWithoutMutation() {
        StockItem item = stockItem(10, 2);

        assertThrows(InsufficientStockException.class, () -> item.release(3));

        assertEquals(10, item.getOnHandQuantity());
        assertEquals(2, item.getReservedQuantity());
    }

    @Test
    void reservationAboveAvailableQuantityIsRejectedWithoutMutation() {
        StockItem item = stockItem(10, 8);

        assertThrows(InsufficientStockException.class, () -> item.reserve(3));

        assertEquals(10, item.getOnHandQuantity());
        assertEquals(8, item.getReservedQuantity());
    }

    private static StockItem stockItem(int onHandQuantity, int reservedQuantity) {
        StockItem item = StockItem.create(UUID.randomUUID(), Instant.parse("2026-01-01T10:00:00Z"));
        ReflectionTestUtils.setField(item, "onHandQuantity", onHandQuantity);
        ReflectionTestUtils.setField(item, "reservedQuantity", reservedQuantity);
        return item;
    }
}
