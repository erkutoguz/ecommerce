package dev.erkut.productservice.observability.metric;

import dev.erkut.productservice.outbox.domain.OutboxStatus;
import dev.erkut.productservice.outbox.persistence.OutboxMessageRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class OutboxMetrics {
    private final OutboxMessageRepository repository;

    public OutboxMetrics(MeterRegistry registry, OutboxMessageRepository repository) {
        this.repository = repository;
        Gauge.builder("outbox.pending.messages", repository, r -> r.countByStatus(OutboxStatus.PENDING)).register(registry);
        Gauge.builder("outbox.in_progress.messages", repository, r -> r.countByStatus(OutboxStatus.IN_PROGRESS)).register(registry);
        Gauge.builder("outbox.expired.claims", repository, OutboxMessageRepository::countExpiredClaims).register(registry);
        Gauge.builder("outbox.oldest.pending.age.seconds", this, OutboxMetrics::oldestPendingAgeSeconds).register(registry);
    }

    private double oldestPendingAgeSeconds() {
        return repository.findOldestPendingMessageCreatedAt()
                .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).toMillis()) / 1_000.0)
                .orElse(0.0);
    }
}
