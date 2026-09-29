package dev.erkut.orderservice.checkout.api;


import dev.erkut.orderservice.checkout.api.request.CheckoutRequest;
import dev.erkut.orderservice.checkout.application.CheckoutService;
import dev.erkut.orderservice.integration.customer.CurrentCustomerResolver;
import dev.erkut.orderservice.order.api.OrderMapper;
import dev.erkut.orderservice.order.api.response.OrderResponse;
import dev.erkut.orderservice.order.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/carts")
public class CheckoutController {

    private final CheckoutService checkoutService;
    private final CurrentCustomerResolver currentCustomerResolver;
    public CheckoutController(
            CheckoutService checkoutService,
            CurrentCustomerResolver currentCustomerResolver
    ) {
        this.checkoutService = checkoutService;
        this.currentCustomerResolver = currentCustomerResolver;
    }

    @PostMapping("/{cartId}/checkout")
    public ResponseEntity<OrderResponse> checkout(
            @PathVariable("cartId") UUID cartId,
            @Valid @RequestBody CheckoutRequest request
    ) {
        UUID customerId = currentCustomerResolver.customerId();
        Order order = checkoutService.checkout(cartId, customerId, request.currency());
        return ResponseEntity
                .created(URI.create("/orders/" + order.getId()))
                .body(OrderMapper.toResponse(order));
    }
}
