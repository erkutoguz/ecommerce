package dev.erkut.orderservice.message.event;

import dev.erkut.orderservice.order.domain.Currency;

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
    public record OrderCheckoutItem(
            UUID productId,
            int quantity
    ) {}
}
