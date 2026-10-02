package dev.erkut.stockservice.outbox.persistence;

import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import dev.erkut.stockservice.outbox.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    @Query(value = "SELECT COUNT(*) FROM outbox_messages WHERE status = 'PENDING'", nativeQuery = true)
    long countPendingMessages();

    @Query(value = "SELECT MIN(created_at) FROM outbox_messages WHERE status = 'PENDING'", nativeQuery = true)
    Optional<Instant> findOldestPendingMessageCreatedAt();
}
