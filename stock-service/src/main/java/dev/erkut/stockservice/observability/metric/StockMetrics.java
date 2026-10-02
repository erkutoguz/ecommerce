package dev.erkut.stockservice.observability.metric;

import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.application.ReservationProcessingOutcome;
import dev.erkut.stockservice.reservation.domain.StockReservationFailureReason;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Component
public class StockMetrics {

    private final Counter reservations;
    private final Counter confirmations;
    private final Counter releases;
    private final Counter insufficientStockFailures;
    private final Counter missingItemFailures;
    private final Counter inactiveItemFailures;
    private final ReservationRepository reservationRepository;
    private final OutboxMessageRepository outboxRepository;

    public StockMetrics(
            MeterRegistry registry,
            ReservationRepository reservationRepository,
            OutboxMessageRepository outboxRepository
    ) {
        this.reservationRepository = reservationRepository;
        this.outboxRepository = outboxRepository;
        this.reservations = registry.counter("stock.reservations.reserved");
        this.confirmations = registry.counter("stock.reservations.confirmed");
        this.releases = registry.counter("stock.reservations.released");
        this.insufficientStockFailures = failureCounter(registry, StockReservationFailureReason.INSUFFICIENT_STOCK);
        this.missingItemFailures = failureCounter(registry, StockReservationFailureReason.ITEM_NOT_FOUND);
        this.inactiveItemFailures = failureCounter(registry, StockReservationFailureReason.ITEM_INACTIVE);

        Gauge.builder("stock.reservations.active", reservationRepository, ReservationRepository::countActiveReservations)
                .register(registry);
        Gauge.builder("stock.reservations.oldest.active.age.seconds", this, StockMetrics::oldestReservationAgeSeconds)
                .register(registry);
        Gauge.builder("stock.outbox.pending.messages", outboxRepository, OutboxMessageRepository::countPendingMessages)
                .register(registry);
        Gauge.builder("stock.outbox.oldest.pending.age.seconds", this, StockMetrics::oldestPendingOutboxAgeSeconds)
                .register(registry);
    }

    public void record(ReservationProcessingOutcome outcome) {
        switch (outcome) {
            case DUPLICATE -> { }
            case RESERVED -> reservations.increment();
            case FAILED_INSUFFICIENT_STOCK -> insufficientStockFailures.increment();
            case FAILED_ITEM_NOT_FOUND -> missingItemFailures.increment();
            case FAILED_ITEM_INACTIVE -> inactiveItemFailures.increment();
            case CONFIRMED -> confirmations.increment();
            case RELEASED -> releases.increment();
        }
    }

    private Counter failureCounter(MeterRegistry registry, StockReservationFailureReason reason) {
        return registry.counter(
                "stock.reservations.failed",
                "reason",
                reason.name().toLowerCase(Locale.ROOT)
        );
    }

    private double oldestReservationAgeSeconds() {
        return reservationRepository.findOldestActiveReservationCreatedAt()
                .map(StockMetrics::ageSeconds)
                .orElse(0.0);
    }

    private double oldestPendingOutboxAgeSeconds() {
        return outboxRepository.findOldestPendingMessageCreatedAt()
                .map(StockMetrics::ageSeconds)
                .orElse(0.0);
    }

    private static double ageSeconds(Instant createdAt) {
        return Math.max(0, Duration.between(createdAt, Instant.now()).toMillis()) / 1_000.0;
    }
}
