package dev.erkut.orderservice.cart.api.admin;

import dev.erkut.orderservice.cart.api.CartMapper;
import dev.erkut.orderservice.cart.api.response.CartResponse;
import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.domain.Cart;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/admin/carts")
public class AdminCartController {

    private final CartService cartService;

    public AdminCartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<CartResponse> getCart(@PathVariable UUID cartId) {
        Cart cart = cartService.getCartByIdForAdmin(cartId);
        return ResponseEntity.ok(CartMapper.toResponse(cart));
    }
}
