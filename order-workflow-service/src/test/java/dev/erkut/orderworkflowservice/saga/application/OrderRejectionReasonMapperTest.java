package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.message.command.ordercommands.OrderRejectionReason;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationFailureReason;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentFailureReason;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaFailureReason;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderRejectionReasonMapperTest {

    @Test
    void from_allStockReservationFailureReasons_shouldMapToOutOfStock() {
        for (StockReservationFailureReason reason : StockReservationFailureReason.values()) {
            assertEquals(OrderRejectionReason.OUT_OF_STOCK, OrderRejectionReasonMapper.from(reason));
        }
    }

    @Test
    void from_sessionExpired_shouldPreserveCanonicalCompensationReason() {
        assertEquals(
                OrderSagaFailureReason.PAYMENT_EXPIRED,
                OrderRejectionReasonMapper.from(PaymentFailureReason.SESSION_EXPIRED)
        );
        assertEquals(OrderRejectionReason.PAYMENT_EXPIRED,
                OrderRejectionReasonMapper.from(OrderSagaFailureReason.PAYMENT_EXPIRED));
        assertEquals(OrderRejectionReason.RESERVATION_EXPIRED,
                OrderRejectionReasonMapper.from(OrderSagaFailureReason.RESERVATION_EXPIRED));
    }
}
