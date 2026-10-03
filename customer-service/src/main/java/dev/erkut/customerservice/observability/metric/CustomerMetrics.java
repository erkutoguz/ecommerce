package dev.erkut.customerservice.observability.metric;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CustomerMetrics {

    private final Counter provisioningCompleted;

    public CustomerMetrics(MeterRegistry meterRegistry) {
        this.provisioningCompleted = meterRegistry.counter("customers.provisioning.completed");
    }

    public void provisioningCompleted() {
        provisioningCompleted.increment();
    }
}
