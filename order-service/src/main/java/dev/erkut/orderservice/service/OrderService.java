package dev.erkut.orderservice.service;

import dev.erkut.orderservice.dev.MockCustomerData;
import dev.erkut.orderservice.dev.MockProductData;
import dev.erkut.orderservice.dto.OrderCreateRequest;
import dev.erkut.orderservice.dto.OrderItemCreateRequest;
import dev.erkut.orderservice.dto.OrderResponse;
import dev.erkut.orderservice.dto.UpdateOrderItemRequest;
import dev.erkut.orderservice.exception.CustomerNotFoundException;
import dev.erkut.orderservice.exception.OrderNotFoundException;
import dev.erkut.orderservice.exception.ProductNotFoundException;
import dev.erkut.orderservice.mapper.OrderMapper;
import dev.erkut.orderservice.model.Order;
import dev.erkut.orderservice.repository.OrderRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {
    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public OrderResponse createOrder(OrderCreateRequest req) {
        // check customer with id req.customerId()
        if(!MockCustomerData.CUSTOMER_IDS.contains(req.customerId())) {
            throw new CustomerNotFoundException("Customer not found with id: " + req.customerId());
        }

        Instant now = Instant.now();
        Order order = Order.create(req.customerId(), req.currency(), now);

        // get product info
        for (OrderItemCreateRequest itemRequest : req.items()) {

            MockProductData.MockProduct product = MockProductData.PRODUCTS.get(itemRequest.itemId());

            if (product == null) {
                throw new ProductNotFoundException("Product not found with id: " + itemRequest.itemId());
            }

            order.addItem(
                    itemRequest.itemId(),
                    product.name(),
                    product.price(),
                    itemRequest.quantity(),
                    now
            );
        }

        Order savedOrder = orderRepository.save(order);
        return OrderMapper.toResponse(savedOrder);
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

    @Transactional
    public OrderResponse confirmOrder(UUID orderId) {
        Order order = findOrderById(orderId);
        order.confirm(Instant.now());
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse rejectOrder(UUID orderId) {
        Order order = findOrderById(orderId);
        order.reject(Instant.now());
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID orderId) {
        Order order = findOrderById(orderId);
        order.cancel(Instant.now());
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderItem(UUID orderId, UUID itemId, UpdateOrderItemRequest req) {
        Order order = findOrderById(orderId);
        order.changeItemQuantity(
                itemId,
                req.quantity(),
                Instant.now()
        );
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse removeOrderItem(UUID orderId, UUID itemId) {
        Order order = findOrderById(orderId);
        order.removeItem(itemId, Instant.now());
        return OrderMapper.toResponse(order);
    }

    private Order findOrderById(UUID orderId) {
       return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found with id: " + orderId));
    }

}
