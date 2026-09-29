package dev.erkut.orderservice.cart.api;

import dev.erkut.orderservice.cart.api.request.CartAddItemRequest;
import dev.erkut.orderservice.cart.api.request.CartItemUpdateRequest;
import dev.erkut.orderservice.cart.api.response.CartResponse;
import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.integration.customer.CurrentCustomerResolver;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartService cartService;
    private final CurrentCustomerResolver currentCustomerResolver;
    public CartController(
            CartService cartService,
            CurrentCustomerResolver currentCustomerResolver
    ) {
        this.cartService = cartService;
        this.currentCustomerResolver = currentCustomerResolver;
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable("cartId") UUID cartId) {
        UUID customerId = currentCustomerResolver.customerId();
        Cart cart = cartService.getCartById(cartId, customerId);
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @GetMapping("/current")
    public ResponseEntity<CartResponse> getCurrentCart() {
        UUID customerId = currentCustomerResolver.customerId();
        Cart cart = cartService.getOpenCartByCustomerId(customerId);
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @PostMapping("{cartId}/items")
    public ResponseEntity<CartResponse> addCartItem(
            @PathVariable("cartId") UUID cartId,
            @Valid @RequestBody CartAddItemRequest request
    ) {
        UUID customerId = currentCustomerResolver.customerId();
        Cart cart = cartService.addCartItem(cartId, customerId, request.productId(), request.quantity());
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @PatchMapping("/{cartId}/items/{productId}")
    public ResponseEntity<CartResponse> changeCartItemQuantity(
            @PathVariable("cartId") UUID cartId,
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody CartItemUpdateRequest request
    ) {
        UUID customerId = currentCustomerResolver.customerId();
        Cart cart = cartService.changeCartItemQuantity(cartId, customerId, productId, request.quantity());
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @DeleteMapping("{cartId}/items/{productId}")
    public ResponseEntity<Void> removeCartItem(
            @PathVariable("cartId") UUID cartId,
            @PathVariable("productId") UUID productId
    ) {
        UUID customerId = currentCustomerResolver.customerId();
        cartService.removeCartItem(cartId, customerId, productId);
        return ResponseEntity.noContent().build();
    }
}
