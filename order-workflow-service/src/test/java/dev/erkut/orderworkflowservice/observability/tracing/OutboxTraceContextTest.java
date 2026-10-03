package dev.erkut.orderworkflowservice.observability.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxTraceContextTest {

    private static final String TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    private Tracer tracer;
    private TestPropagator propagator;
    private OutboxTraceContext traceContext;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        propagator = new TestPropagator();
        traceContext = new OutboxTraceContext(tracer, propagator);
    }

    @Test
    void capture_shouldPersistOnlyW3cTraceHeaders() {
        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(mock(TraceContext.class));

        assertEquals(new OutboxTraceContext.Headers(TRACEPARENT, "vendor=value"), traceContext.capture());
    }

    @Test
    void runWithParent_shouldExtractAndScopeThePersistedContext() {
        Span relaySpan = mock(Span.class);
        Span.Builder builder = mock(Span.Builder.class);
        when(builder.name("workflow.outbox.relay")).thenReturn(builder);
        when(builder.start()).thenReturn(relaySpan);
        propagator.builder = builder;
        AtomicBoolean scoped = new AtomicBoolean();
        when(tracer.withSpan(relaySpan)).thenAnswer(invocation -> {
            scoped.set(true);
            return (Tracer.SpanInScope) () -> scoped.set(false);
        });

        traceContext.runWithParent(
                new OutboxTraceContext.Headers(TRACEPARENT, "vendor=value"),
                () -> assertTrue(scoped.get())
        );

        assertEquals(TRACEPARENT, propagator.extracted.get("traceparent"));
        assertEquals("vendor=value", propagator.extracted.get("tracestate"));
        assertFalse(scoped.get());
    }

    @Test
    void runWithParent_withoutTraceContext_shouldRunWithoutCreatingASpan() {
        AtomicBoolean executed = new AtomicBoolean();

        traceContext.runWithParent(null, () -> executed.set(true));

        assertTrue(executed.get());
    }

    private static class TestPropagator implements Propagator {

        private final Map<String, String> extracted = new HashMap<>();
        private Span.Builder builder;

        @Override
        public java.util.List<String> fields() {
            return java.util.List.of("traceparent", "tracestate", "baggage");
        }

        @Override
        public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {
            setter.set(carrier, "traceparent", TRACEPARENT);
            setter.set(carrier, "tracestate", "vendor=value");
            setter.set(carrier, "baggage", "must-not-be-persisted");
        }

        @Override
        public <C> Span.Builder extract(C carrier, Getter<C> getter) {
            extracted.put("traceparent", getter.get(carrier, "traceparent"));
            extracted.put("tracestate", getter.get(carrier, "tracestate"));
            return builder;
        }
    }
}
