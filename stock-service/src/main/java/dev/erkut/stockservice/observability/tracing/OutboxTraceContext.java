package dev.erkut.stockservice.observability.tracing;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class OutboxTraceContext {

    private static final String TRACEPARENT = "traceparent";
    private static final String TRACESTATE = "tracestate";

    private final Tracer tracer;
    private final Propagator propagator;

    public OutboxTraceContext(Tracer tracer, Propagator propagator) {
        this.tracer = tracer;
        this.propagator = propagator;
    }

    public Headers capture() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return null;
        }

        Map<String, String> carrier = new HashMap<>();
        propagator.inject(currentSpan.context(), carrier, (values, key, value) -> {
            if (TRACEPARENT.equals(key) || TRACESTATE.equals(key)) {
                values.put(key, value);
            }
        });

        String traceparent = carrier.get(TRACEPARENT);
        return traceparent == null ? null : new Headers(traceparent, carrier.get(TRACESTATE));
    }

    public void runWithParent(Headers headers, Runnable operation) {
        if (headers == null || headers.traceparent() == null) {
            operation.run();
            return;
        }

        Map<String, String> carrier = new HashMap<>();
        carrier.put(TRACEPARENT, headers.traceparent());
        if (headers.tracestate() != null) {
            carrier.put(TRACESTATE, headers.tracestate());
        }

        Span relaySpan = propagator.extract(carrier, Map::get)
                .name("stock.outbox.relay")
                .start();
        try (Tracer.SpanInScope ignored = tracer.withSpan(relaySpan)) {
            operation.run();
        } catch (RuntimeException | Error exception) {
            relaySpan.error(exception);
            throw exception;
        } finally {
            relaySpan.end();
        }
    }

    public record Headers(String traceparent, String tracestate) {
    }
}
