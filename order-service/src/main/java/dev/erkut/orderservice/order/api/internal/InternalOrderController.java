package dev.erkut.orderservice.order.api.internal;

import dev.erkut.orderservice.integration.customer.CurrentCustomerResolver;
import dev.erkut.orderservice.order.application.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/orders")
public class InternalOrderController {

    private final OrderService orderService;
    private final CurrentCustomerResolver currentCustomerResolver;

    public InternalOrderController(
            OrderService orderService,
            CurrentCustomerResolver currentCustomerResolver
    ) {
        this.orderService = orderService;
        this.currentCustomerResolver = currentCustomerResolver;
    }

    @GetMapping("/{orderId}/ownership")
    public ResponseEntity<OrderOwnershipResponse> getOwnership(@PathVariable UUID orderId) {
        UUID customerId = currentCustomerResolver.customerId();

        if (!orderService.belongsToCustomer(orderId, customerId)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(new OrderOwnershipResponse(orderId, customerId));
    }
}
