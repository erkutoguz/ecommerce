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
                order.getCustomerId(),
                toOrderItemResponses(order.getItems()),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }

    private static OrderItemResponse toResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getItemId(),
                item.getItemNameSnapshot(),
                item.getItemPriceSnapshot(),
                item.getQuantity()
        );
    }

    private static List<OrderItemResponse> toOrderItemResponses(List<OrderItem> items) {
        return items.stream()
                .map(OrderMapper::toResponse)
                .toList();
    }
}