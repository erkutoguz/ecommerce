package dev.erkut.productservice.outbox.persistence;

import dev.erkut.productservice.outbox.domain.OutboxMessage;
import dev.erkut.productservice.outbox.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxMessageRepository extends JpaRepository<OutboxMessage, UUID> {
    List<OutboxMessage> findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus status);

    long countByStatus(OutboxStatus status);
    @Query(value = """
            WITH candidates AS (
                SELECT candidate.id
                FROM outbox_messages candidate
                WHERE ((candidate.status = 'PENDING' AND candidate.next_attempt_at <= clock_timestamp())
                    OR (candidate.status = 'IN_PROGRESS' AND candidate.claim_until <= clock_timestamp()))
                  AND NOT EXISTS (
                      SELECT 1 FROM outbox_messages earlier
                      WHERE earlier.aggregate_id = candidate.aggregate_id
                        AND earlier.status <> 'PUBLISHED'
                        AND (earlier.created_at, earlier.id) < (candidate.created_at, candidate.id)
                  )
                ORDER BY candidate.created_at, candidate.id
                LIMIT :batchSize
                FOR UPDATE OF candidate SKIP LOCKED
            ), claimed AS (
                UPDATE outbox_messages message
                SET status = 'IN_PROGRESS',
                    claim_token = gen_random_uuid(),
                    claim_until = clock_timestamp() + (:leaseSeconds * interval '1 second'),
                    attempt_count = attempt_count + 1
                FROM candidates
                WHERE message.id = candidates.id
                RETURNING message.id, message.claim_token
            )
            SELECT id, claim_token FROM claimed
            """, nativeQuery = true)
    List<Object[]> claimEligibleMessages(
            @Param("batchSize") int batchSize,
            @Param("leaseSeconds") long leaseSeconds
    );

    @Query(value = """
            UPDATE outbox_messages
            SET status = 'PUBLISHED', published_at = :publishedAt,
                claim_token = NULL, claim_until = NULL
            WHERE id = :messageId AND status = 'IN_PROGRESS' AND claim_token = :claimToken
            """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int markClaimedMessagePublished(
            @Param("messageId") UUID messageId,
            @Param("claimToken") UUID claimToken,
            @Param("publishedAt") java.time.Instant publishedAt
    );

    @Query(value = """
            UPDATE outbox_messages
            SET status = 'PENDING', claim_token = NULL, claim_until = NULL,
                next_attempt_at = :retryAt
            WHERE id = :messageId AND status = 'IN_PROGRESS' AND claim_token = :claimToken
            """, nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int rescheduleClaimedMessage(
            @Param("messageId") UUID messageId,
            @Param("claimToken") UUID claimToken,
            @Param("retryAt") java.time.Instant retryAt
    );

    @org.springframework.data.jpa.repository.Query(value = "SELECT COUNT(*) FROM outbox_messages WHERE status = 'IN_PROGRESS' AND claim_until <= CURRENT_TIMESTAMP", nativeQuery = true)
    long countExpiredClaims();

    @org.springframework.data.jpa.repository.Query(value = "SELECT MIN(created_at) FROM outbox_messages WHERE status = 'PENDING'", nativeQuery = true)
    java.util.Optional<java.time.Instant> findOldestPendingMessageCreatedAt();
}
