package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.message.command.StockCommandType;
import dev.erkut.stockservice.reservation.application.ReservationService;
import dev.erkut.stockservice.stock.application.StockService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;

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
    public void listenStockCommand(MessageEnvelope envelope) throws JacksonException {
        consumerUtil.validateEnvelope(envelope);

        StockCommandType commandType = StockCommandType.from(envelope.messageType());
        if (commandType == null) {
            return;
        }

        switch (commandType) {
            case RESERVE_STOCK_COMMAND -> {
                ReserveStockCommand command = consumerUtil.deserialize(envelope.payload(), ReserveStockCommand.class);
                reservationService.handleReserveStock(envelope, command);
            }

        }
    }

}
