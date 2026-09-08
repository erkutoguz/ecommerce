package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.message.command.StockCommandType;
import dev.erkut.stockservice.reservation.application.ReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StockCommandsListener {

    private final ConsumerUtil consumerUtil;
    private final ReservationService reservationService;
    public StockCommandsListener(
            ConsumerUtil consumerUtil,
            ReservationService reservationService
    ) {
        this.consumerUtil = consumerUtil;
        this.reservationService = reservationService;
    }

    @KafkaListener(
            topics = "${kafka.topic.stock-commands}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void listenStockCommand(MessageEnvelope envelope) {
        consumerUtil.validateEnvelope(envelope);

        StockCommandType commandType = StockCommandType.from(envelope.messageType());
        if (commandType == null) {
            throw new IllegalArgumentException("Unsupported stock command type: " + envelope.messageType());
        }

        switch (commandType) {
            case RESERVE_STOCK_COMMAND -> {
                ReserveStockCommand command = consumerUtil.deserialize(envelope.payload(), ReserveStockCommand.class);
                reservationService.handleReserveStock(envelope, command);
            }

        }
    }

}
