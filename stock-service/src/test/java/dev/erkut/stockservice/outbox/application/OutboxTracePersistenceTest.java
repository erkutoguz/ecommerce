package dev.erkut.stockservice.outbox.application;

import dev.erkut.stockservice.message.event.StockReservedEvent;
import dev.erkut.stockservice.observability.tracing.OutboxTraceContext;
import dev.erkut.stockservice.outbox.domain.OutboxMessage;
import dev.erkut.stockservice.outbox.persistence.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxTracePersistenceTest {

    @Test
    void createEvent_shouldPersistCapturedW3cContextOnOutboxRow() {
        OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
        OutboxTraceContext traceContext = mock(OutboxTraceContext.class);
        when(traceContext.capture()).thenReturn(new OutboxTraceContext.Headers(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "vendor=value"
        ));
        OutboxService outboxService = new OutboxService(repository, new JsonMapper(), traceContext);
        UUID orderId = UUID.randomUUID();
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);

        outboxService.createStockReservedEvent(
                new StockReservedEvent(orderId, Instant.parse("2026-01-01T10:00:00Z")),
                Instant.parse("2026-01-01T10:00:00Z")
        );

        verify(repository).save(captor.capture());
        assertEquals(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                captor.getValue().getTraceparent()
        );
        assertEquals("vendor=value", captor.getValue().getTracestate());
    }
}
