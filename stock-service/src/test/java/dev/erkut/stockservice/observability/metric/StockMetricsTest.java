package dev.erkut.stockservice.observability.metric;

import dev.erkut.stockservice.observability.metric.StockMetrics;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import dev.erkut.stockservice.reservation.application.ReservationProcessingOutcome;
import dev.erkut.stockservice.reservation.persistence.ReservationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StockMetricsTest {

    @Test
    void record_shouldCountNewReservationOutcomesAndIgnoreDuplicates() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        StockMetrics metrics = new StockMetrics(
                registry,
                mock(ReservationRepository.class),
                mock(OutboxMessageRepository.class)
        );

        metrics.record(ReservationProcessingOutcome.RESERVED);
        metrics.record(ReservationProcessingOutcome.FAILED_INSUFFICIENT_STOCK);
        metrics.record(ReservationProcessingOutcome.FAILED_ITEM_NOT_FOUND);
        metrics.record(ReservationProcessingOutcome.FAILED_ITEM_INACTIVE);
        metrics.record(ReservationProcessingOutcome.CONFIRMED);
        metrics.record(ReservationProcessingOutcome.RELEASED);
        metrics.record(ReservationProcessingOutcome.DUPLICATE);

        assertEquals(1.0, registry.counter("stock.reservations.reserved").count());
        assertEquals(1.0, registry.counter("stock.reservations.confirmed").count());
        assertEquals(1.0, registry.counter("stock.reservations.released").count());
        assertEquals(1.0, registry.counter(
                "stock.reservations.failed", "reason", "insufficient_stock"
        ).count());
        assertEquals(1.0, registry.counter(
                "stock.reservations.failed", "reason", "item_not_found"
        ).count());
        assertEquals(1.0, registry.counter(
                "stock.reservations.failed", "reason", "item_inactive"
        ).count());
    }

    @Test
    void gauges_shouldReflectActiveReservationsAndPendingOutboxState() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        OutboxMessageRepository outboxRepository = mock(OutboxMessageRepository.class);
        when(reservationRepository.countActiveReservations()).thenReturn(2L);
        when(reservationRepository.findOldestActiveReservationCreatedAt())
                .thenReturn(Optional.of(Instant.now().minusSeconds(90)));
        when(outboxRepository.countPendingMessages()).thenReturn(3L);
        when(outboxRepository.findOldestPendingMessageCreatedAt())
                .thenReturn(Optional.of(Instant.now().minusSeconds(30)));

        new StockMetrics(registry, reservationRepository, outboxRepository);

        assertEquals(2.0, registry.get("stock.reservations.active").gauge().value());
        assertEquals(90.0, registry.get("stock.reservations.oldest.active.age.seconds").gauge().value(), 1.0);
        assertEquals(3.0, registry.get("stock.outbox.pending.messages").gauge().value());
        assertEquals(30.0, registry.get("stock.outbox.oldest.pending.age.seconds").gauge().value(), 1.0);
    }

    @Test
    void emptyGauges_shouldBeZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReservationRepository reservationRepository = mock(ReservationRepository.class);
        OutboxMessageRepository outboxRepository = mock(OutboxMessageRepository.class);
        when(reservationRepository.countActiveReservations()).thenReturn(0L);
        when(reservationRepository.findOldestActiveReservationCreatedAt()).thenReturn(Optional.empty());
        when(outboxRepository.countPendingMessages()).thenReturn(0L);
        when(outboxRepository.findOldestPendingMessageCreatedAt()).thenReturn(Optional.empty());

        new StockMetrics(registry, reservationRepository, outboxRepository);

        assertEquals(0.0, registry.get("stock.reservations.active").gauge().value());
        assertEquals(0.0, registry.get("stock.reservations.oldest.active.age.seconds").gauge().value());
        assertEquals(0.0, registry.get("stock.outbox.pending.messages").gauge().value());
        assertEquals(0.0, registry.get("stock.outbox.oldest.pending.age.seconds").gauge().value());
    }
}
