package dev.erkut.orderservice.observability.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxTraceContextTest {

    private static final String TRACEPARENT =
            "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";
    private static final String TRACESTATE = "vendor=value";

    private Tracer tracer;
    private Propagator propagator;
    private OutboxTraceContext outboxTraceContext;

    @BeforeEach
    void setUp() {
        tracer = mock(Tracer.class);
        propagator = new TestPropagator();
        outboxTraceContext = new OutboxTraceContext(tracer, propagator);
    }

    @Test
    void capture_shouldPersistOnlyW3cTraceContextFields() {
        Span currentSpan = mock(Span.class);
        TraceContext traceContext = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(currentSpan);
        when(currentSpan.context()).thenReturn(traceContext);

        OutboxTraceContext.Headers headers = outboxTraceContext.capture();

        assertEquals(new OutboxTraceContext.Headers(TRACEPARENT, TRACESTATE), headers);
    }

    @Test
    void runWithParent_shouldScopeExtractedParentAndCloseScope() {
        Span relaySpan = mock(Span.class);
        Span.Builder spanBuilder = mock(Span.Builder.class);
        Tracer.SpanInScope spanInScope = mock(Tracer.SpanInScope.class);
        AtomicBoolean scoped = new AtomicBoolean();
        TestPropagator testPropagator = (TestPropagator) propagator;
        testPropagator.spanBuilder = spanBuilder;
        when(spanBuilder.name("outbox.relay")).thenReturn(spanBuilder);
        when(spanBuilder.start()).thenReturn(relaySpan);
        when(tracer.withSpan(relaySpan)).thenAnswer(invocation -> {
            scoped.set(true);
            return (Tracer.SpanInScope) () -> {
                scoped.set(false);
                spanInScope.close();
            };
        });

        outboxTraceContext.runWithParent(
                new OutboxTraceContext.Headers(TRACEPARENT, TRACESTATE),
                () -> assertTrue(scoped.get())
        );

        assertEquals(TRACEPARENT, testPropagator.extracted.get("traceparent"));
        assertEquals(TRACESTATE, testPropagator.extracted.get("tracestate"));
        assertFalse(scoped.get());
        verify(spanInScope).close();
        verify(relaySpan).end();
    }

    @Test
    void runWithParent_withoutContext_shouldRunWithoutCreatingSpan() {
        AtomicBoolean ran = new AtomicBoolean();

        outboxTraceContext.runWithParent(null, () -> ran.set(true));

        assertTrue(ran.get());
        verify(tracer, never()).withSpan(any());
    }

    private static class TestPropagator implements Propagator {

        private final Map<String, String> extracted = new java.util.HashMap<>();
        private Span.Builder spanBuilder;

        @Override
        public java.util.List<String> fields() {
            return java.util.List.of("traceparent", "tracestate", "baggage");
        }

        @Override
        public <C> void inject(TraceContext context, C carrier, Setter<C> setter) {
            setter.set(carrier, "traceparent", TRACEPARENT);
            setter.set(carrier, "tracestate", TRACESTATE);
            setter.set(carrier, "baggage", "customer=do-not-persist");
        }

        @Override
        public <C> Span.Builder extract(C carrier, Getter<C> getter) {
            extracted.put("traceparent", getter.get(carrier, "traceparent"));
            extracted.put("tracestate", getter.get(carrier, "tracestate"));
            return spanBuilder;
        }
    }
}
