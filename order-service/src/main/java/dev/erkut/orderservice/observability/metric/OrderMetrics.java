package dev.erkut.orderservice.observability.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import dev.erkut.orderservice.order.domain.OrderRejectionReason;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class OrderMetrics {

    private final Counter checkoutAttempts;
    private final Counter acceptedCheckouts;
    private final Counter failedCheckouts;
    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.checkoutAttempts = registry.counter("checkout.attempts");
        this.acceptedCheckouts = registry.counter("checkout.accepted");
        this.failedCheckouts = registry.counter("checkout.failed");
    }

    public void checkoutAttempted() {
        checkoutAttempts.increment();
    }

    public void checkoutAccepted() {
        acceptedCheckouts.increment();
    }

    public void checkoutFailed() {
        failedCheckouts.increment();
    }

    public void orderRejected(OrderRejectionReason reason) {
        registry.counter(
                "orders.rejected",
                "reason",
                reason.name().toLowerCase(Locale.ROOT)
        ).increment();
    }
}
