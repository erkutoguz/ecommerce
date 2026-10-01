package dev.erkut.orderservice.observability.metric;

import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
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

        Gauge.builder(
                "outbox.pending.messages",
                repository,
                outboxRepository -> outboxRepository.countByStatus(OutboxStatus.PENDING)
        ).register(registry);

        Gauge.builder(
                "outbox.oldest.pending.age.seconds",
                this,
                OutboxMetrics::oldestPendingAgeSeconds
        ).register(registry);
    }

    private double oldestPendingAgeSeconds() {
        return repository.findOldestCreatedAtByStatus(OutboxStatus.PENDING)
                .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).toMillis()) / 1_000.0)
                .orElse(0.0);
    }
}
