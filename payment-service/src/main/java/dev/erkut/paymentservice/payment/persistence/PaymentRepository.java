package dev.erkut.paymentservice.payment.persistence;

import dev.erkut.paymentservice.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByProviderPaymentId(String providerPaymentId);
}
