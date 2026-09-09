package dev.erkut.orderservice.messaging.kafka.consumer;

import dev.erkut.orderservice.message.MessageEnvelope;
import dev.erkut.orderservice.message.command.OrderCommandType;
import dev.erkut.orderservice.message.command.RejectOrderCommand;
import dev.erkut.orderservice.order.application.OrderService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCommandsListener {

    private final ConsumerUtil consumerUtil;
    private final OrderService orderService;

    public OrderCommandsListener(ConsumerUtil consumerUtil, OrderService orderService) {
        this.consumerUtil = consumerUtil;
        this.orderService = orderService;
    }

    @KafkaListener(
            groupId = "${spring.kafka.consumer.group-id}",
            topics = "${kafka.topic.order-commands}"
    )
    public void listenOrderCommands(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);

        OrderCommandType commandType = OrderCommandType.from(envelope.messageType());
        if(commandType == null) {
            throw new IllegalArgumentException("Unsupported order command type: " + envelope.messageType());
        }

        switch (commandType) {
            case REJECT_ORDER_COMMAND -> {
                RejectOrderCommand command = consumerUtil.deserialize(envelope.payload(), RejectOrderCommand.class);
                orderService.handleRejectOrderCommand(
                        envelope,
                        command
                );
            }
        }
    }
}
