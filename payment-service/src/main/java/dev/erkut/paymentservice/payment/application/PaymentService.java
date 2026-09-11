package dev.erkut.paymentservice.payment.application;

import dev.erkut.paymentservice.inbox.application.InboxService;
import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.message.command.InitiatePaymentCommand;
import dev.erkut.paymentservice.message.event.PaymentCompletedEvent;
import dev.erkut.paymentservice.message.event.PaymentFailedEvent;
import dev.erkut.paymentservice.outbox.application.OutboxService;
import dev.erkut.paymentservice.payment.domain.Currency;
import dev.erkut.paymentservice.payment.domain.Payment;
import dev.erkut.paymentservice.payment.domain.PaymentStatus;
import dev.erkut.paymentservice.payment.persistence.PaymentRepository;
import dev.erkut.paymentservice.payment.application.exception.PaymentNotFoundException;
import dev.erkut.paymentservice.provider.payment.PaymentProvider;
import dev.erkut.paymentservice.provider.payment.PaymentSession;
import dev.erkut.paymentservice.provider.payment.stripe.inbox.application.WebhookEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final InboxService inboxService;
    private final PaymentProvider paymentProvider;
    private final WebhookEventService webhookEventService;
    private final OutboxService outboxService;
    public PaymentService(
            PaymentRepository paymentRepository,
            InboxService inboxService,
            PaymentProvider paymentProvider,
            WebhookEventService webhookEventService,
            OutboxService outboxService
    ) {
        this.paymentRepository = paymentRepository;
        this.inboxService = inboxService;
        this.paymentProvider = paymentProvider;
        this.webhookEventService = webhookEventService;
        this.outboxService = outboxService;
    }

    @Transactional
    public void handleInitiatePaymentCommand(MessageEnvelope envelope, InitiatePaymentCommand command) {
        validateInitiatePaymentCommand(envelope, command);

        Instant now = Instant.now();

        if(isDuplicate(envelope, command.orderId(), now)) {
            return;
        }

        Currency currency = CurrencyMapper.from(command.currency());
        Payment payment = Payment.create(command.orderId(), command.totalAmount(), currency, now);
        PaymentSession session =
                paymentProvider.initializePaymentSession(command.orderId(), command.totalAmount(), currency);

        payment.markAwaitingCustomerAction(session.providerPaymentId(), session.checkoutUrl(), now);

        paymentRepository.save(payment);
    }

    @Transactional
    public void handlePaymentCompleted(
            String eventId,
            String eventType,
            String providerPaymentId,
            Instant occurredAt
    ) {
        validatePaymentEvent(eventId, eventType, providerPaymentId, occurredAt);

        Instant now = Instant.now();

        if (isWebhookDuplicate(eventId, eventType, providerPaymentId, now)) {
            return;
        }

        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found for provider payment id: " + providerPaymentId
                ));

        payment.markCompleted(occurredAt, now);
        outboxService.handlePaymentCompletedEvent(
                new PaymentCompletedEvent(payment.getOrderId()),
                now
        );

    }

    @Transactional
    public void handleCheckoutSessionExpired(
            String eventId,
            String eventType,
            String providerPaymentId,
            Instant occurredAt
    ) {
        validatePaymentEvent(eventId, eventType, providerPaymentId, occurredAt);

        Instant now = Instant.now();

        if (isWebhookDuplicate(eventId, eventType, providerPaymentId, now)) {
            return;
        }

        Payment payment = paymentRepository.findByProviderPaymentId(providerPaymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found for provider payment id: " + providerPaymentId
                ));

        if (payment.getStatus() == PaymentStatus.COMPLETED
                || payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }

        payment.markFailed(occurredAt, now);
        outboxService.handlePaymentFailedEvent(
                new PaymentFailedEvent(
                        payment.getOrderId(),
                        PaymentFailureReasonMapper.toEvent(PaymentFailureReason.EXPIRED)
                ),
                now
        );
    }

    private void validatePaymentEvent(
            String eventId,
            String eventType,
            String providerPaymentId,
            Instant occurredAt
    ) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Provider event id cannot be null or blank");
        }
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("Event type cannot be null or blank");
        }
        if (providerPaymentId == null || providerPaymentId.isBlank()) {
            throw new IllegalArgumentException("Provider payment id cannot be null or blank");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("Occurrence time cannot be null");
        }
    }

    private void validateInitiatePaymentCommand(MessageEnvelope envelope, Object event) {
        if (envelope == null) {
            throw new IllegalArgumentException("Message envelope cannot be null");
        }
        if (event == null) {
            throw new IllegalArgumentException("Initiate payment command cannot be null");
        }

    }

    private boolean isWebhookDuplicate(
            String eventId,
            String eventType,
            String providerPaymentId,
            Instant receivedAt
    ) {
        return !webhookEventService.tryRegister(
                eventId,
                eventType,
                providerPaymentId,
                receivedAt
        );
    }

    private boolean isDuplicate(
            MessageEnvelope envelope,
            UUID orderId,
            Instant now
    ) {
        return !inboxService.tryRegister(
                envelope.messageId(),
                envelope.messageType(),
                orderId,
                now
        );
    }
}
