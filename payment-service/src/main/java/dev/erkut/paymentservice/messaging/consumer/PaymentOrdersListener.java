package dev.erkut.paymentservice.messaging.consumer;

import dev.erkut.paymentservice.message.MessageEnvelope;
import dev.erkut.paymentservice.message.command.PaymentCommandType;
import dev.erkut.paymentservice.message.command.InitiatePaymentCommand;
import dev.erkut.paymentservice.payment.application.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentOrdersListener {

    private final PaymentService paymentService;
    private final ConsumerUtil consumerUtil;

    public PaymentOrdersListener(PaymentService paymentService, ConsumerUtil consumerUtil) {
        this.paymentService = paymentService;
        this.consumerUtil = consumerUtil;
    }

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${kafka.topic.payment-commands}"
    )
    public void handlePaymentCommands(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);

        PaymentCommandType commandType = PaymentCommandType.from(envelope.messageType());
        if(commandType == null) {
            throw new IllegalArgumentException("Unsupported order event type: " + envelope.messageType());
        }

        switch (commandType) {
            case INITIATE_PAYMENT_COMMAND -> {
                InitiatePaymentCommand command =
                        consumerUtil.deserialize(envelope.payload(), InitiatePaymentCommand.class);
                paymentService.handleInitiatePaymentCommand(envelope, command);
            }
        }



    }
}
