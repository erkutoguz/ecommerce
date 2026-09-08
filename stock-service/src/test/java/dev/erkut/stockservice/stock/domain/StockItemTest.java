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

    private static StockItem stockItem(int onHandQuantity, int reservedQuantity) {
        StockItem item = StockItem.create(UUID.randomUUID(), Instant.parse("2026-01-01T10:00:00Z"));
        ReflectionTestUtils.setField(item, "onHandQuantity", onHandQuantity);
        ReflectionTestUtils.setField(item, "reservedQuantity", reservedQuantity);
        return item;
    }
}
