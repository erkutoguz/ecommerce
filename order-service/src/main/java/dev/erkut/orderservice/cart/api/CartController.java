package dev.erkut.orderservice.cart.api;

import dev.erkut.orderservice.cart.api.request.CartAddItemRequest;
import dev.erkut.orderservice.cart.api.request.CartItemUpdateRequest;
import dev.erkut.orderservice.cart.api.response.CartItemResponse;
import dev.erkut.orderservice.cart.api.response.CartResponse;
import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartItem;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/carts")
public class CartController {

    private final CartService cartService;
    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable("cartId") UUID cartId) {
        Cart cart = cartService.getCartById(cartId);
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @GetMapping("/current")
    public ResponseEntity<CartResponse> getOpenCartByCustomerId(
            @RequestParam("customerId") UUID customerId
    ) {
        Cart cart = cartService.getOpenCartByCustomerId(customerId);
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @PostMapping("{cartId}/items")
    public ResponseEntity<CartResponse> addCartItem(
            @PathVariable("cartId") UUID cartId,
            @Valid @RequestBody CartAddItemRequest request) {

        Cart cart = cartService.addCartItem(cartId, request.productId(), request.quantity());
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @PatchMapping("/{cartId}/items/{productId}")
    public ResponseEntity<CartResponse> changeCartItemQuantity(
            @PathVariable("cartId") UUID cartId,
            @PathVariable("productId") UUID productId,
            @Valid @RequestBody CartItemUpdateRequest request
    ) {
       Cart cart = cartService.changeCartItemQuantity(cartId, productId, request.quantity());
       return ResponseEntity.ok(CartMapper.toResponse(cart));
    }

    @DeleteMapping("{cartId}/items/{productId}")
    public ResponseEntity<Void> removeCartItem(
            @PathVariable("cartId") UUID cartId,
            @PathVariable("productId") UUID productId) {

        cartService.removeCartItem(cartId, productId);
        return ResponseEntity.noContent().build();
    }
}
