package dev.erkut.stockservice.reservation.persistence;

import dev.erkut.stockservice.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    @Query(value = "SELECT COUNT(*) FROM reservations WHERE status = 'RESERVED'", nativeQuery = true)
    long countActiveReservations();

    @Query(value = "SELECT MIN(created_at) FROM reservations WHERE status = 'RESERVED'", nativeQuery = true)
    Optional<Instant> findOldestActiveReservationCreatedAt();
}
