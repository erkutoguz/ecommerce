package dev.erkut.orderworkflowservice.saga.persistence;

import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderSagaRepository extends JpaRepository<OrderSaga, UUID> {

    @Query(value = "SELECT COUNT(*) FROM order_sagas WHERE state NOT IN ('COMPLETED', 'FAILED')", nativeQuery = true)
    long countActiveSagas();

    @Query(value = "SELECT MIN(created_at) FROM order_sagas WHERE state NOT IN ('COMPLETED', 'FAILED')", nativeQuery = true)
    Optional<Instant> findOldestActiveCreatedAt();
}
