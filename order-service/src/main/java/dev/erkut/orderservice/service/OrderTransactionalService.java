package dev.erkut.orderservice.service;

import dev.erkut.orderservice.model.Order;
import dev.erkut.orderservice.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderTransactionalService {
    private final OrderRepository orderRepository;

    public OrderTransactionalService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Order saveOrder(Order order) {
         return orderRepository.save(order);
    }
}
