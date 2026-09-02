package dev.erkut.orderservice.cart.application;

import dev.erkut.orderservice.cart.application.exception.CartNotFoundException;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import dev.erkut.orderservice.cart.persistence.CartRepository;
import dev.erkut.orderservice.integration.customer.CustomerClient;
import dev.erkut.orderservice.integration.customer.CustomerLookupResponse;
import dev.erkut.orderservice.integration.customer.CustomerStatus;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
public class CartService {

    private final CartRepository cartRepository;
    private final CartTransactionalService transactionalService;
    private final CustomerClient customerClient;
    private final ProductClient productClient;

    public CartService(
            CartRepository cartRepository,
            CartTransactionalService transactionalService,
            CustomerClient customerClient, ProductClient productClient
    ) {
        this.cartRepository = cartRepository;
        this.transactionalService = transactionalService;
        this.customerClient = customerClient;
        this.productClient = productClient;
    }


    public Cart createCart(UUID customerId) {
        validateCustomer(customerId);
        return transactionalService.create(customerId);
    }

    public Cart getOpenCartByCustomerId(UUID customerId) {
        if (customerId == null) {
            throw new IllegalArgumentException("Customer id cannot be null");
        }

        return cartRepository
                .findByCustomerIdAndStatusIn(
                        customerId,
                        List.of(CartStatus.ACTIVE, CartStatus.CHECKOUT_LOCKED)
                )
                .orElseGet(() -> createCart(customerId));
    }

    @Transactional(readOnly = true)
    public Cart getCartById(UUID cartId) {
        if(cartId == null) {
            throw new IllegalArgumentException("Cart id cannot be null");
        }

        return cartRepository.findWithCartItemsById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + cartId));
    }

    public Cart addCartItem(
            UUID cartId,
            UUID productId,
            int quantity
    ) {
        if (cartId == null) {
            throw new IllegalArgumentException("Cart id cannot be null");
        }

        if(productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        validateProducts(List.of(productId));

        return transactionalService.addCartItem(cartId, productId, quantity, Instant.now());
    }

    @Transactional
    public void removeCartItem(UUID cartId, UUID productId) {
        if (cartId == null) {
            throw new IllegalArgumentException("Cart id cannot be null");
        }

        if (productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        Cart cart = cartRepository.findWithCartItemsById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + cartId));

        cart.removeCartItem(productId, Instant.now());
    }

    @Transactional
    public Cart changeCartItemQuantity(UUID cartId, UUID productId, int quantity) {
        if (cartId == null) {
            throw new IllegalArgumentException("Cart id cannot be null");
        }

        if (productId == null) {
            throw new IllegalArgumentException("Product id cannot be null");
        }

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }

        Cart cart = cartRepository.findWithCartItemsById(cartId)
                .orElseThrow(() -> new CartNotFoundException("Cart not found with id: " + cartId));

        cart.changeCartItemQuantity(productId, quantity, Instant.now());
        return cart;
    }

    private void validateCustomer(UUID customerId) {
        if(customerId == null) {
            throw new IllegalArgumentException("Customer id cannot be null");
        }

        CustomerLookupResponse customer = customerClient.getCustomerDetail(customerId);
        if(customer.status() != CustomerStatus.ACTIVE) {
            throw new InvalidCustomerStateException("Customer is not active");
        }
    }

    void validateProducts(List<UUID> productIds) {
        List<ProductLookupResponse> products = productClient
                .getProductsByIds(new ProductLookupRequest(productIds));

        Set<UUID> missingIds = new HashSet<>(productIds);

        for(ProductLookupResponse product : products) {
            missingIds.remove(product.productId());
        }

        if(!missingIds.isEmpty()) {
            throw new ProductNotFoundException("Product not found with id(s): " + missingIds);
        }

        for(ProductLookupResponse product : products) {
            if(product.status() != ProductStatus.ACTIVE) {
                throw new InvalidProductStateException("Product is not active");
            }
        }
    }

}
