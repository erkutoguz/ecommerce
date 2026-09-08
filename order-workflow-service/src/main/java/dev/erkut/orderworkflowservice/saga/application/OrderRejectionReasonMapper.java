package dev.erkut.orderworkflowservice.saga.application;

import dev.erkut.orderworkflowservice.message.command.OrderRejectionReason;
import dev.erkut.orderworkflowservice.message.event.StockReservationFailureReason;

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
}