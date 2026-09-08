package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.message.command.OrderRejectionReason;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailureReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderRejectionReasonMapperTest {

    @Test
    void from_allStockReservationFailureReasons_shouldMapToOutOfStock() {
        for (StockReservationFailureReason reason : StockReservationFailureReason.values()) {
            assertEquals(OrderRejectionReason.OUT_OF_STOCK, OrderRejectionReasonMapper.from(reason));
        }
    }
}
