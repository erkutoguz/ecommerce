package dev.erkut.orderservice.cart.api;

import dev.erkut.orderservice.cart.api.response.CartItemResponse;
import dev.erkut.orderservice.cart.api.response.CartResponse;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartItem;

import java.util.List;

public class CartMapper {

    public static CartResponse toResponse(Cart cart) {
        return new CartResponse(
                cart.getId(),
                cart.getCustomerId(),
                cart.getStatus(),
                toCartItemListResponse(cart.getCartItems()),
                cart.getUpdatedAt(),
                cart.getCreatedAt());
    }

    private static List<CartItemResponse> toCartItemListResponse(List<CartItem> cartItems) {
        return cartItems.stream().map(CartMapper::toCartItemResponse).toList();
    }

    private static CartItemResponse toCartItemResponse(CartItem cartItem) {
        return new CartItemResponse(cartItem.getProductId(), cartItem.getQuantity());
    }
}
