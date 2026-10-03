package dev.erkut.orderworkflowservice.observability.metric;

import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class SagaMetrics {

    private final Counter started;
    private final Counter completed;
    private final Counter failed;
    private final OrderSagaRepository repository;

    public SagaMetrics(MeterRegistry registry, OrderSagaRepository repository) {
        this.repository = repository;
        this.started = registry.counter("sagas.started");
        this.completed = registry.counter("sagas.completed");
        this.failed = registry.counter("sagas.failed");

        Gauge.builder("sagas.active", repository, OrderSagaRepository::countActiveSagas)
                .register(registry);
        Gauge.builder("sagas.oldest.active.age.seconds", this, SagaMetrics::oldestActiveAgeSeconds)
                .register(registry);
    }

    public void sagaStarted() {
        started.increment();
    }

    public void sagaCompleted() {
        completed.increment();
    }

    public void sagaFailed() {
        failed.increment();
    }

    private double oldestActiveAgeSeconds() {
        return repository.findOldestActiveCreatedAt()
                .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).toMillis()) / 1_000.0)
                .orElse(0.0);
    }
}
