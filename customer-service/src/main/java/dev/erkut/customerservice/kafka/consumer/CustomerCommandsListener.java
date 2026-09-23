package dev.erkut.customerservice.kafka.consumer;

import dev.erkut.customerservice.customer.application.CustomerService;
import dev.erkut.customerservice.message.MessageEnvelope;
import dev.erkut.customerservice.message.command.CreateCustomerCommand;
import dev.erkut.customerservice.message.command.CustomerCommandType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CustomerCommandsListener {

    private final ConsumerUtil consumerUtil;
    private final CustomerService customerService;

    public CustomerCommandsListener(
            ConsumerUtil consumerUtil,
            CustomerService customerService
    ) {
        this.consumerUtil = consumerUtil;
        this.customerService = customerService;
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
                customerService.handleCreateCustomerCommand(
                        envelope,
                        command
                );
            }
        }
    }
}
