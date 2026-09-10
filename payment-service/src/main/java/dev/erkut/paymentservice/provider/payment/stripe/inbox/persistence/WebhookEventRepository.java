package dev.erkut.paymentservice.provider.payment.stripe.inbox.persistence;

import dev.erkut.paymentservice.provider.payment.stripe.inbox.domain.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, String> {

    @Modifying
    @Query(value = """
            INSERT INTO webhook_events (
                event_id,
                event_type,
                provider_object_id,
                received_at
            ) VALUES (
                :eventId,
                :eventType,
                :providerObjectId,
                :receivedAt
            )
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("providerObjectId") String providerObjectId,
            @Param("receivedAt") Instant receivedAt
    );
}
