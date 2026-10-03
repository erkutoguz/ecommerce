package dev.erkut.customerservice.kafka.consumer;

import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.message.MessageEnvelope;
import dev.erkut.customerservice.message.command.CreateCustomerCommand;
import dev.erkut.customerservice.observability.metric.CustomerMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerCommandsListenerMetricsTest {

    @Test
    void provisioningMetricCountsOnlyNewProvisioningTransitions() {
        ConsumerUtil consumerUtil = mock(ConsumerUtil.class);
        CustomerService customerService = mock(CustomerService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        CustomerMetrics customerMetrics = new CustomerMetrics(meterRegistry);
        CustomerCommandsListener listener = new CustomerCommandsListener(
                consumerUtil,
                customerService,
                customerMetrics
        );
        CreateCustomerCommand command = new CreateCustomerCommand(UUID.randomUUID(), "customer@example.test");
        when(consumerUtil.deserialize(any(), eq(CreateCustomerCommand.class))).thenReturn(command);
        when(customerService.handleCreateCustomerCommand(any(), eq(command)))
                .thenReturn(true, false);

        listener.listenCustomerCommands(envelope());
        listener.listenCustomerCommands(envelope());

        assertEquals(1.0, meterRegistry.get("customers.provisioning.completed").counter().count());
    }

    private MessageEnvelope envelope() {
        return new MessageEnvelope(
                UUID.randomUUID(),
                "CREATE_CUSTOMER_COMMAND",
                Instant.parse("2026-10-03T00:00:00Z"),
                null
        );
    }
}
