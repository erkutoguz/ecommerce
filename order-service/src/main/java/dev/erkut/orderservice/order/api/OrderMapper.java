package dev.erkut.orderservice.order.api;

import dev.erkut.orderservice.order.api.response.OrderItemResponse;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderItem;
import java.util.List;

public class OrderMapper {

    public static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getSourceCartId(),
                order.getCustomerId(),
                toOrderItemResponses(order.getOrderItems()),
                order.getStatus(),
                order.getRejectionReason(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getConfirmedAt(),
                order.getRejectedAt()
        );
    }

    private static OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getProductPriceSnapshot(),
                item.getQuantity()
        );
    }

    private static List<OrderItemResponse> toOrderItemResponses(List<OrderItem> items) {
        return items.stream()
                .map(OrderMapper::toResponse)
                .toList();
    }
}
