package dev.erkut.orderservice.checkout.application;

import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartItem;
import dev.erkut.orderservice.integration.customer.CustomerClient;
import dev.erkut.orderservice.integration.customer.CustomerLookupResponse;
import dev.erkut.orderservice.integration.customer.CustomerStatus;
import dev.erkut.orderservice.integration.customer.InvalidCustomerStateException;
import dev.erkut.orderservice.integration.product.*;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class CheckoutService {
    private final CartService cartService;
    private final CustomerClient customerClient;
    private final ProductClient productClient;
    private final CheckoutTransactionalService transactionalService;

    public CheckoutService(
            CartService cartService,
            CustomerClient customerClient,
            ProductClient productClient,
            CheckoutTransactionalService transactionalService
    ) {
        this.cartService = cartService;
        this.customerClient = customerClient;
        this.productClient = productClient;
        this.transactionalService = transactionalService;
    }

    public Order checkout(UUID cartId, Currency currency) {
        if (cartId == null) {
            throw new IllegalArgumentException("Cart id cannot be null");
        }

        if (currency == null) {
            throw new IllegalArgumentException("Currency cannot be null");
        }

        Cart cart = cartService.getCartById(cartId);

        validateCustomer(cart.getCustomerId());

        List<ProductLookupResponse> products =
                getValidatedProducts(cart.getCartItems());

        List<OrderLineSnapshot> snapshots =
                createOrderLineSnapshots(cart, products);

        return transactionalService.checkout(
                cart.getId(),
                cart.getVersion(),
                currency,
                snapshots,
                Instant.now()
        );
    }

    private void validateCustomer(UUID customerId) {
        CustomerLookupResponse customer = customerClient.getCustomerDetail(customerId);

        if (customer.status() != CustomerStatus.ACTIVE) {
            throw new InvalidCustomerStateException("Customer is not active");
        }
    }

    private List<ProductLookupResponse> getValidatedProducts(List<CartItem> cartItems) {
        if (cartItems.isEmpty()) {
            throw new IllegalStateException("Empty cart cannot be checked out");
        }

        List<UUID> productIds = cartItems.stream()
                .map(CartItem::getProductId)
                .toList();

        List<ProductLookupResponse> products =
                productClient.getProductsByIds(new ProductLookupRequest(productIds));

        Set<UUID> missingIds = new HashSet<>(productIds);

        for (ProductLookupResponse product : products) {
            missingIds.remove(product.productId());

            if (product.status() != ProductStatus.ACTIVE) {
                throw new InvalidProductStateException("Product is not active: " + product.productId());
            }
        }

        if (!missingIds.isEmpty()) {
            throw new ProductNotFoundException("Product not found with id(s): " + missingIds);
        }

        return products;
    }

    private List<OrderLineSnapshot> createOrderLineSnapshots(
            Cart cart,
            List<ProductLookupResponse> products
    ) {
        Map<UUID, ProductLookupResponse> productsWithIds = new HashMap<>();

        for (ProductLookupResponse product : products) {
            productsWithIds.put(product.productId(), product);
        }

        return cart.getCartItems().stream()
                .map(cartItem -> {
                    ProductLookupResponse product = productsWithIds.get(cartItem.getProductId());

                    return new OrderLineSnapshot(
                            product.productId(),
                            product.name(),
                            product.price(),
                            cartItem.getQuantity());
                }).toList();
    }
}
