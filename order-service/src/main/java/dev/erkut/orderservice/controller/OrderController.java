package dev.erkut.orderservice.controller;

import dev.erkut.orderservice.dto.*;
import dev.erkut.orderservice.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        Page<OrderResponse> response =
                orderService.getOrders(customerId, page, size);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable("orderId") UUID orderId) {
        OrderResponse response = orderService.getOrderById(orderId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<OrderResponse> confirmOrder(@PathVariable("orderId") UUID orderId) {
        OrderResponse response = orderService.confirmOrder(orderId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<OrderResponse> rejectOrder(@PathVariable("orderId") UUID orderId) {
        OrderResponse response = orderService.rejectOrder(orderId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable("orderId") UUID orderId) {
        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping("/{orderId}/items")
    public ResponseEntity<OrderResponse> addOrderItem(
            @PathVariable("orderId") UUID orderId,
            @Valid @RequestBody OrderItemRequest req) {
        OrderResponse response = orderService.addOrderItem(orderId, req);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderResponse> updateOrderItem(
            @PathVariable("orderId") UUID orderId,
            @PathVariable("itemId") UUID itemId,
            @Valid @RequestBody OrderItemUpdateRequest req
    ) {
        OrderResponse response = orderService.updateOrderItem(orderId, itemId, req);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @DeleteMapping("/{orderId}/items/{itemId}")
    public ResponseEntity<OrderResponse> removeOrderItem(@PathVariable("orderId") UUID orderId,
                                                         @PathVariable("itemId") UUID itemId) {
        OrderResponse response = orderService.removeOrderItem(orderId, itemId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
