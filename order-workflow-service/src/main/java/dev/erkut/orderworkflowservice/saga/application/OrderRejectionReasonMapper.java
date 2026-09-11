package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.message.command.ordercommands.OrderRejectionReason;
import dev.erkut.orderworkflowservice.message.event.paymentevents.PaymentFailureReason;
import dev.erkut.orderworkflowservice.message.event.stockevents.StockReservationFailureReason;
import dev.erkut.orderworkflowservice.saga.domain.OrderSagaFailureReason;

public final class OrderRejectionReasonMapper {

    private OrderRejectionReasonMapper() {}

    public static OrderRejectionReason from(StockReservationFailureReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Stock reservation failure reason cannot be null");
        }

        return switch (reason) {
            case ITEM_NOT_FOUND,
                 ITEM_INACTIVE,
                 INSUFFICIENT_STOCK ->
                    OrderRejectionReason.OUT_OF_STOCK;
        };
    }

    public static OrderSagaFailureReason from(PaymentFailureReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Payment failure reason cannot be null");
        }

        return switch (reason) {
            case SESSION_EXPIRED -> OrderSagaFailureReason.PAYMENT_EXPIRED;
        };
    }

    public static OrderRejectionReason from(OrderSagaFailureReason reason) {
        if (reason == null) {
            throw new IllegalArgumentException("Order saga failure reason cannot be null");
        }

        return switch (reason) {
            case PAYMENT_EXPIRED -> OrderRejectionReason.PAYMENT_EXPIRED;
            case RESERVATION_EXPIRED -> OrderRejectionReason.RESERVATION_EXPIRED;
        };
    }
}
