package dev.erkut.orderservice.outbox.domain;

import dev.erkut.orderservice.outbox.domain.exception.InvalidOutboxMessageException;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_outbox_messages")
public class OutboxMessage {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 40)
    private OutboxMessageType messageType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private JsonNode payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxMessage () {}

    private OutboxMessage (UUID aggregateId, OutboxMessageType messageType, JsonNode payload, Instant now) {
        if (aggregateId == null) {
            throw new InvalidOutboxMessageException("Aggregate id cannot be null");
        }

        if (messageType == null) {
            throw new InvalidOutboxMessageException("Message type cannot be null");
        }

        if (payload == null) {
            throw new InvalidOutboxMessageException("Payload cannot be null");
        }

        if (now == null) {
            throw new InvalidOutboxMessageException("Creation time cannot be null");
        }


        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.messageType = messageType;
        this.payload = payload;
        this.status = OutboxStatus.PENDING;
        this.createdAt = now;
        this.publishedAt = null;
    }

    public static OutboxMessage create(
            UUID aggregateId,
            OutboxMessageType messageType,
            JsonNode payload,
            Instant now
    ) {
        return new OutboxMessage(aggregateId, messageType, payload, now);
    }

    public UUID getId() {
        return id;
    }

    public OutboxStatus getStatus() {
        return status;
    }

    public OutboxMessageType getMessageType() {
        return messageType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public JsonNode getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void markPublished(Instant now) {
        if(now == null) {
            throw new InvalidOutboxMessageException("Publish time cannot be null");
        }

        if(status != OutboxStatus.PENDING) {
            throw new InvalidOutboxMessageException("Only pending outbox messages can be published");
        }

        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
    }

}
