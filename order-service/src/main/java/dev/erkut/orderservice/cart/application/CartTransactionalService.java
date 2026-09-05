package dev.erkut.orderservice.cart.application;

import dev.erkut.orderservice.cart.application.exception.CartNotFoundException;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class CartTransactionalService {
    private final CartRepository cartRepository;

    public CartTransactionalService(CartRepository cartRepository) {
        this.cartRepository = cartRepository;
    }

    @Transactional
    public Cart create(UUID customerId) {
        Cart cart = Cart.create(customerId, Instant.now());
        return cartRepository.save(cart);
    }

    @Transactional
    public Cart addCartItem(UUID cartId, UUID productId, int quantity, Instant now) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + cartId));

        cart.addCartItem(productId, quantity, now);
        return cart;
    }
}
