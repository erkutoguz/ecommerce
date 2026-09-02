package dev.erkut.orderservice.order.application;

import dev.erkut.orderservice.integration.customer.CustomerClient;
import dev.erkut.orderservice.integration.customer.CustomerLookupResponse;
import dev.erkut.orderservice.integration.customer.CustomerStatus;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.InvalidProductStateException;
import dev.erkut.orderservice.integration.product.ProductClient;
import dev.erkut.orderservice.integration.product.ProductLookupRequest;
import dev.erkut.orderservice.integration.product.ProductLookupResponse;
import dev.erkut.orderservice.integration.product.ProductNotFoundException;
import dev.erkut.orderservice.integration.product.ProductStatus;
import dev.erkut.orderservice.order.api.OrderMapper;
import dev.erkut.orderservice.order.api.request.OrderCreateRequest;
import dev.erkut.orderservice.order.api.request.OrderItemRequest;
import dev.erkut.orderservice.order.api.request.OrderItemUpdateRequest;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.exception.OrderNotFoundException;
import dev.erkut.orderservice.order.persistence.OrderRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final OrderTransactionalService orderTransactionalService;
    public OrderService(OrderRepository orderRepository, CustomerClient customerClient, ProductClient productClient, OrderTransactionalService orderTransactionalService) {
        this.orderRepository = orderRepository;
        this.customerClient = customerClient;
        this.productClient = productClient;
        this.orderTransactionalService = orderTransactionalService;
    }

    public OrderResponse createOrder(OrderCreateRequest req) {
        CustomerLookupResponse customer = customerClient.getCustomerDetail(req.customerId());

        if(customer.status() != CustomerStatus.ACTIVE) {
            throw new InvalidCustomerStateException("Customer is not active: " + customer.customerId());
        }

        Map<UUID, Integer> productQtyMap = new HashMap<>();
        req.items().forEach(item -> {
            productQtyMap.put(item.itemId(), productQtyMap.getOrDefault(item.itemId(), 0) + item.quantity());
        });

        ProductLookupRequest lookupRequest = new ProductLookupRequest(new ArrayList<>(productQtyMap.keySet()));
        List<ProductLookupResponse> products = productClient.getProductsByIds(lookupRequest);

        products.stream()
                .filter(product -> product.status() != ProductStatus.ACTIVE)
                .findFirst()
                .ifPresent(product -> {
                    throw new InvalidProductStateException("Product is not active: " + product.productId());
                });

        Instant now = Instant.now();
        Order order = Order.create(customer.customerId(), req.currency(), now);

        products.forEach(p -> {
            order.addItem(p.productId(),
                    p.name(),
                    p.price(),
                    productQtyMap.get(p.productId()),
                    now
            );
        });
        Order savedOrder = orderTransactionalService.saveOrder(order);

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
    public OrderResponse addOrderItem(UUID orderId, OrderItemRequest req) {
        Order order = findOrderById(orderId);
        List<ProductLookupResponse> products = productClient.getProductsByIds(
                new ProductLookupRequest(List.of(req.itemId())));

        if (products == null || products.isEmpty()) {
            throw new ProductNotFoundException("Product not found: " + req.itemId());
        }

        ProductLookupResponse product = products.getFirst();
        order.addItem(
                req.itemId(),
                product.name(),
                product.price(),
                req.quantity(),
                Instant.now()
        );
        return OrderMapper.toResponse(order);
    }

    @Transactional
    public OrderResponse updateOrderItem(UUID orderId, UUID itemId, OrderItemUpdateRequest req) {
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
