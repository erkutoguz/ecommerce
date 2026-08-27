package dev.erkut.orderservice.repository;

import dev.erkut.orderservice.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<Order> findWithItemsById(UUID id);

    Page<Order> findAllByCustomerId(UUID customerId, Pageable pageable);

}
