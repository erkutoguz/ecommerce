package dev.erkut.paymentservice.payment.persistence;

import dev.erkut.paymentservice.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;
import java.time.Instant;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);

    @Query(value = "SELECT COUNT(*) FROM payments WHERE status = :status", nativeQuery = true)
    long countByStatus(@Param("status") String status);

    @Query(value = "SELECT MIN(created_at) FROM payments WHERE status = :status", nativeQuery = true)
    Optional<Instant> findOldestCreatedAtByStatus(@Param("status") String status);
}
