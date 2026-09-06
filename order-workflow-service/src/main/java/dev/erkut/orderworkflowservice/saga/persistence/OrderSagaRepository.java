package dev.erkut.orderworkflowservice.saga.persistence;

import dev.erkut.orderworkflowservice.saga.domain.OrderSaga;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderSagaRepository extends JpaRepository<OrderSaga, UUID> {
}
