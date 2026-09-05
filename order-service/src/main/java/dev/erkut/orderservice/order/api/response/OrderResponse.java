package dev.erkut.orderservice.order.api.response;

import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import dev.erkut.orderservice.order.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        UUID sourceCartId,
        UUID customerId,
        List<OrderItemResponse> items,
        OrderStatus status,
        OrderRejectionReason rejectionReason,
        Currency currency,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt,
        Instant rejectedAt
) {}
