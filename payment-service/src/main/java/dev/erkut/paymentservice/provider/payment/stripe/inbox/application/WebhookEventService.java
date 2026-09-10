package dev.erkut.paymentservice.provider.payment.stripe.inbox.application;

import dev.erkut.paymentservice.provider.payment.stripe.inbox.persistence.WebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class WebhookEventService {
    private final WebhookEventRepository webhookEventRepository;

    public WebhookEventService(WebhookEventRepository webhookEventRepository) {
        this.webhookEventRepository = webhookEventRepository;
    }

    @Transactional
    public boolean tryRegister(
            String eventId,
            String eventType,
            String providerPaymentId,
            Instant receivedAt
    ) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Webhook event id cannot be null");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Webhook event type cannot be null or blank");
        }

        if (receivedAt == null) {
            throw new IllegalArgumentException("Received time cannot be null");
        }

        return webhookEventRepository.insertIfAbsent(
                eventId,
                eventType,
                providerPaymentId,
                receivedAt
        ) == 1;
    }
}
