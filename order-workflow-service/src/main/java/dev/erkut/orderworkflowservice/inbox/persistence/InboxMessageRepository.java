package dev.erkut.orderworkflowservice.inbox.persistence;

import dev.erkut.orderworkflowservice.inbox.domain.InboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface InboxMessageRepository extends JpaRepository<InboxMessage, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO inbox_messages (
                message_id,
                message_type,
                aggregate_id,
                processed_at
            ) VALUES (
                :messageId,
                :messageType,
                :aggregateId,
                :processedAt
            )
            ON CONFLICT (message_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("messageId") UUID messageId,
            @Param("messageType") String messageType,
            @Param("aggregateId") UUID aggregateId,
            @Param("processedAt") Instant processedAt
    );
}
