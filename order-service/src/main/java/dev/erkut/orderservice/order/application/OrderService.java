package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.order.api.OrderMapper;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order createFromCheckout(
            UUID sourceCartId,
            UUID customerId,
            Currency currency,
            List<OrderLineSnapshot> itemSnapshots,
            Instant now
    ) {
        Order order = Order.create(sourceCartId, customerId, currency, itemSnapshots, now);
        return orderRepository.save(order);
    }

    @Transactional
    public Order markStockReserved(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.markStockReserved(now);
        return order;
    }

    @Transactional
    public Order markPaymentUnknown(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.markPaymentUnknown(now);
        return order;
    }

    @Transactional
    public Order markPaymentCompleted(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.markPaymentCompleted(now);
        return order;
    }

    @Transactional
    public Order confirm(UUID orderId, Instant now) {
        Order order = findOrderById(orderId);
        order.confirm(now);
        return order;
    }

    @Transactional
    public Order reject(UUID orderId, OrderRejectionReason reason, Instant now) {
        Order order = findOrderById(orderId);
        order.reject(reason, now);
        return order;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> getOrders(UUID customerId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending()
                .and(Sort.by(Sort.Direction.DESC, "id")));

        Page<Order> orders = customerId == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findAllByCustomerId(customerId, pageable);

        return orders.map(OrderMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderById(UUID orderId) {
        Order order = orderRepository.findWithItemsById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
        return OrderMapper.toResponse(order);
    }

    private Order findOrderById(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
    }
}
