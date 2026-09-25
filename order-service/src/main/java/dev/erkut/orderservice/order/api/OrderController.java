package dev.erkut.orderservice.order.api;

import dev.erkut.orderservice.integration.customer.CurrentCustomerResolver;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.application.OrderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;
    private final CurrentCustomerResolver currentCustomerResolver;
    public OrderController(
            OrderService orderService,
            CurrentCustomerResolver currentCustomerResolver
    ) {
        this.orderService = orderService;
        this.currentCustomerResolver = currentCustomerResolver;
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size
    ) {
        UUID customerId = currentCustomerResolver.customerId();
        Page<OrderResponse> response =
                orderService.getOrders(customerId, page, size);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable("orderId") UUID orderId) {
        UUID customerId = currentCustomerResolver.customerId();
        OrderResponse response = orderService.getOrderById(orderId, customerId);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
