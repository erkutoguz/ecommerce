package dev.erkut.orderservice.order.api.response;

import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID customerId,
        List<OrderItemResponse> items,
        OrderStatus status,
        Currency currency,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt
) {}
