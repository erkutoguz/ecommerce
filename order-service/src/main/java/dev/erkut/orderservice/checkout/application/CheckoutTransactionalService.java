package dev.erkut.orderservice.checkout.application;

import dev.erkut.orderservice.cart.application.CartService;
import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.checkout.application.exception.CartChangedDuringCheckoutException;
import dev.erkut.orderservice.order.application.OrderService;
import dev.erkut.orderservice.order.domain.Currency;
import dev.erkut.orderservice.order.domain.Order;
import dev.erkut.orderservice.order.domain.OrderLineSnapshot;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutTransactionalService {
    private final CartService cartService;
    private final OrderService orderService;

    public CheckoutTransactionalService(CartService cartService, OrderService orderService) {
        this.cartService = cartService;
        this.orderService = orderService;
    }

    @Transactional
    public Order checkout(
            UUID sourceCartId,
            long expectedCartVersion,
            Currency currency,
            List<OrderLineSnapshot> itemSnapshots,
            Instant now
    ) {
       Cart cart = cartService.getCartById(sourceCartId);

       if(cart.getVersion() != expectedCartVersion) {
           throw new CartChangedDuringCheckoutException("Cart changed during checkout");
       }

       cart.lockForCheckout(now);

       return orderService.createFromCheckout(
               sourceCartId,
               cart.getCustomerId(),
               currency,
               itemSnapshots,
               now
       );
    }
}
