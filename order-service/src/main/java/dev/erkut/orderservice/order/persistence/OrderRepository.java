package dev.erkut.orderservice.order.persistence;

import dev.erkut.orderservice.order.domain.Order;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findWithItemsById(UUID id);

    Page<Order> findAllByCustomerId(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = "orderItems")
    @Query("select o from Order o")
    Page<Order> findAllWithItems(Pageable pageable);

    @EntityGraph(attributePaths = "orderItems")
    Optional<Order> findWithItemsByIdAndCustomerId(UUID id, UUID customerId);
}
