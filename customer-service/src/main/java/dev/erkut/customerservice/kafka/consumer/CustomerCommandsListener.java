package dev.erkut.customerservice.kafka.consumer;

import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.message.MessageEnvelope;
import dev.erkut.customerservice.message.command.CreateCustomerCommand;
import dev.erkut.customerservice.message.command.CustomerCommandType;
import dev.erkut.customerservice.observability.metric.CustomerMetrics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CustomerCommandsListener {

    private final ConsumerUtil consumerUtil;
    private final CustomerService customerService;
    private final CustomerMetrics customerMetrics;

    public CustomerCommandsListener(
            ConsumerUtil consumerUtil,
            CustomerService customerService,
            CustomerMetrics customerMetrics
    ) {
        this.consumerUtil = consumerUtil;
        this.customerService = customerService;
        this.customerMetrics = customerMetrics;
    }

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${kafka.topic.customer-commands}"
    )
    public void listenCustomerCommands(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);

        CustomerCommandType commandType = CustomerCommandType.from(envelope.messageType());
        if(commandType == null) {
            throw new IllegalArgumentException("Unsupported order command type: " + envelope.messageType());
        }

        switch (commandType) {
            case CREATE_CUSTOMER_COMMAND -> {
                CreateCustomerCommand command = consumerUtil.deserialize(envelope.payload(), CreateCustomerCommand.class);
                boolean provisioned = customerService.handleCreateCustomerCommand(
                        envelope,
                        command
                );
                if (provisioned) {
                    customerMetrics.provisioningCompleted();
                }
            }
        }
    }
}
