package dev.erkut.orderservice.controller;

import dev.erkut.orderservice.dto.OrderCreateRequest;
import dev.erkut.orderservice.dto.OrderResponse;
import dev.erkut.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderCreateRequest req) {
        OrderResponse response = orderService.createOrder(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
