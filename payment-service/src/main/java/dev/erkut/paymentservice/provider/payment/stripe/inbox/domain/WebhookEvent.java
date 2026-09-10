package dev.erkut.paymentservice.provider.payment.stripe.inbox.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "webhook_events")
public class WebhookEvent {
    @Id
    @Column(name = "event_id")
    private String eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "provider_object_id")
    private String providerObjectId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected WebhookEvent() {}

    private WebhookEvent(String eventId, String eventType, String providerObjectId, Instant receivedAt) {
        if(eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Webhook event id cannot be null");
        }
        if(eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Webhook event type cannot be null");
        }
        if(receivedAt == null) {
            throw new IllegalArgumentException("Received time cannot be null");
        }

        this.eventId = eventId;
        this.eventType = eventType;
        this.providerObjectId = providerObjectId;
        this.receivedAt = receivedAt;
    }

    public static WebhookEvent create(String eventId, String eventType, String providerObjectId, Instant receivedAt) {
        return new WebhookEvent(eventId, eventType, providerObjectId, receivedAt);
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getProviderObjectId() {
        return providerObjectId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}
