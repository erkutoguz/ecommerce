package dev.erkut.orderworkflowservice.observability.metric;

import dev.erkut.orderworkflowservice.saga.persistence.OrderSagaRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SagaMetricsTest {

    @Test
    void activeGauges_shouldReflectRepositoryStateAndOldestSagaAge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderSagaRepository repository = mock(OrderSagaRepository.class);
        when(repository.countActiveSagas()).thenReturn(4L);
        when(repository.findOldestActiveCreatedAt()).thenReturn(Optional.of(Instant.now().minusSeconds(90)));

        SagaMetrics metrics = new SagaMetrics(registry, repository);

        assertEquals(4.0, registry.get("sagas.active").gauge().value());
        assertEquals(90.0, registry.get("sagas.oldest.active.age.seconds").gauge().value(), 1.0);
    }

    @Test
    void oldestActiveAge_shouldBeZeroWhenThereAreNoActiveSagas() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OrderSagaRepository repository = mock(OrderSagaRepository.class);
        when(repository.countActiveSagas()).thenReturn(0L);
        when(repository.findOldestActiveCreatedAt()).thenReturn(Optional.empty());

        new SagaMetrics(registry, repository);

        assertEquals(0.0, registry.get("sagas.active").gauge().value());
        assertEquals(0.0, registry.get("sagas.oldest.active.age.seconds").gauge().value());
    }
}
