package dev.erkut.orderservice.service;

import dev.erkut.orderservice.dev.MockCustomerData;
import dev.erkut.orderservice.dev.MockProductData;
import dev.erkut.orderservice.dto.OrderCreateRequest;
import dev.erkut.orderservice.dto.OrderItemCreateRequest;
import dev.erkut.orderservice.dto.OrderResponse;
import dev.erkut.orderservice.exception.CustomerNotFoundException;
import dev.erkut.orderservice.exception.ProductNotFoundException;
import dev.erkut.orderservice.mapper.OrderMapper;
import dev.erkut.orderservice.model.Order;
import dev.erkut.orderservice.repository.OrderRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;

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
}
