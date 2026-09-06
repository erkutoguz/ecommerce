package dev.erkut.orderworkflowservice.message.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCheckoutStartedEvent(
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        Currency currency,
        List<OrderCheckoutItem> items
) {
    public static final String MESSAGE_TYPE = OrderEventType.ORDER_CHECKOUT_STARTED.name();

    public record OrderCheckoutItem(
            UUID productId,
            int quantity
    ) {}
}
