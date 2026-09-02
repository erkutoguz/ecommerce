package dev.erkut.orderservice.cart.persistence;

import dev.erkut.orderservice.cart.domain.Cart;
import dev.erkut.orderservice.cart.domain.CartStatus;
import org.jspecify.annotations.NonNull;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CartRepository extends JpaRepository<Cart, UUID> {
    @EntityGraph(attributePaths = "cartItems")
    Optional<Cart> findWithCartItemsById(UUID id);

    @EntityGraph(attributePaths = "cartItems")
    Optional<Cart> findByCustomerIdAndStatusIn(UUID customerId, Collection<CartStatus> statuses);
}
