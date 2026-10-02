package dev.erkut.paymentservice.observability.metric;

import dev.erkut.paymentservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.paymentservice.payment.application.PaymentFailureReason;
import dev.erkut.paymentservice.payment.persistence.PaymentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Component
public class PaymentMetrics {

    private final Counter started;
    private final Counter completed;
    private final Counter expired;
    private final PaymentRepository paymentRepository;
    private final OutboxMessageRepository outboxRepository;

    public PaymentMetrics(
            MeterRegistry registry,
            PaymentRepository paymentRepository,
            OutboxMessageRepository outboxRepository
    ) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.started = registry.counter("payments.started");
        this.completed = registry.counter("payments.completed");
        this.expired = registry.counter(
                "payments.failed",
                "reason",
                PaymentFailureReason.EXPIRED.name().toLowerCase(Locale.ROOT)
        );

        Gauge.builder("payments.pending", paymentRepository,
                        repository -> repository.countByStatus("AWAITING_CUSTOMER_ACTION"))
                .register(registry);
        Gauge.builder("payments.oldest.pending.age.seconds", this, PaymentMetrics::oldestPendingPaymentAgeSeconds)
                .register(registry);
        Gauge.builder("payment.outbox.pending.messages", outboxRepository, OutboxMessageRepository::countPendingMessages)
                .register(registry);
        Gauge.builder("payment.outbox.oldest.pending.age.seconds", this, PaymentMetrics::oldestPendingOutboxAgeSeconds)
                .register(registry);
    }

    public void paymentStarted() {
        started.increment();
    }

    public void paymentCompleted() {
        completed.increment();
    }

    public void paymentExpired() {
        expired.increment();
    }

    private double oldestPendingPaymentAgeSeconds() {
        return paymentRepository.findOldestCreatedAtByStatus("AWAITING_CUSTOMER_ACTION")
                .map(PaymentMetrics::ageSeconds)
                .orElse(0.0);
    }

    private double oldestPendingOutboxAgeSeconds() {
        return outboxRepository.findOldestPendingMessageCreatedAt()
                .map(PaymentMetrics::ageSeconds)
                .orElse(0.0);
    }

    private static double ageSeconds(Instant createdAt) {
        return Math.max(0, Duration.between(createdAt, Instant.now()).toMillis()) / 1_000.0;
    }
}
