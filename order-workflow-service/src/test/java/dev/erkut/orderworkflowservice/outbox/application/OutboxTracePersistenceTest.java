package dev.erkut.orderworkflowservice.outbox.application;

import dev.erkut.orderworkflowservice.message.command.stockcommands.ReserveStockCommand;
import dev.erkut.orderworkflowservice.observability.tracing.OutboxTraceContext;
import dev.erkut.orderworkflowservice.outbox.domain.OutboxMessage;
import dev.erkut.orderworkflowservice.outbox.persistence.OutboxMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxTracePersistenceTest {

    @Test
    void createCommand_shouldPersistCapturedW3cContextOnOutboxRow() {
        OutboxMessageRepository repository = mock(OutboxMessageRepository.class);
        JsonMapper jsonMapper = new JsonMapper();
        OutboxTraceContext traceContext = mock(OutboxTraceContext.class);
        when(traceContext.capture()).thenReturn(new OutboxTraceContext.Headers(
                "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01",
                "vendor=value"
        ));
        OutboxService outboxService = new OutboxService(repository, jsonMapper, traceContext);
        UUID orderId = UUID.randomUUID();
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);

        outboxService.createReserveStockCommand(
                new ReserveStockCommand(orderId, List.of()),
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
