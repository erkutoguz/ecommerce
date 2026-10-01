package dev.erkut.orderservice.observability.metric;

import dev.erkut.orderservice.outbox.domain.OutboxStatus;
import dev.erkut.orderservice.outbox.persistence.OutboxMessageRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {

    @Test
    void gauges_shouldReflectPendingRowsAndOldestPendingAge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(3L);
        when(repository.findOldestCreatedAtByStatus(OutboxStatus.PENDING))
                .thenReturn(Optional.of(Instant.now().minusSeconds(75)));

        new OutboxMetrics(registry, repository);

        assertEquals(3.0, registry.get("outbox.pending.messages").gauge().value());
        double oldestAge = registry.get("outbox.oldest.pending.age.seconds").gauge().value();
        assertEquals(75.0, oldestAge, 1.0);
        verify(repository).countByStatus(OutboxStatus.PENDING);
        verify(repository).findOldestCreatedAtByStatus(OutboxStatus.PENDING);
    }

    @Test
    void oldestPendingAge_shouldBeZeroWhenThereAreNoPendingRows() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
        when(repository.countByStatus(OutboxStatus.PENDING)).thenReturn(0L);
        when(repository.findOldestCreatedAtByStatus(OutboxStatus.PENDING)).thenReturn(Optional.empty());

        new OutboxMetrics(registry, repository);

        assertEquals(0.0, registry.get("outbox.pending.messages").gauge().value());
        assertEquals(0.0, registry.get("outbox.oldest.pending.age.seconds").gauge().value());
    }
}
