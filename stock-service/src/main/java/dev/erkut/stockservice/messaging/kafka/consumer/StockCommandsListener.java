package dev.erkut.stockservice.messaging.kafka.consumer;

import dev.erkut.stockservice.message.MessageEnvelope;
import dev.erkut.stockservice.message.command.ConfirmStockReservationCommand;
import dev.erkut.stockservice.message.command.ReleaseStockReservationCommand;
import dev.erkut.stockservice.message.command.ReserveStockCommand;
import dev.erkut.stockservice.message.command.StockCommandType;
import dev.erkut.stockservice.observability.metric.StockMetrics;
import dev.erkut.stockservice.reservation.application.ReservationService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class StockCommandsListener {

    private final ConsumerUtil consumerUtil;
    private final ReservationService reservationService;
    private final StockMetrics stockMetrics;
    public StockCommandsListener(
            ConsumerUtil consumerUtil,
            ReservationService reservationService,
            StockMetrics stockMetrics
    ) {
        this.consumerUtil = consumerUtil;
        this.reservationService = reservationService;
        this.stockMetrics = stockMetrics;
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
                stockMetrics.record(reservationService.handleReserveStock(envelope, command));
            }
            case CONFIRM_STOCK_RESERVATION_COMMAND -> {
                ConfirmStockReservationCommand command =
                        consumerUtil.deserialize(envelope.payload(), ConfirmStockReservationCommand.class);
                stockMetrics.record(reservationService.handleConfirmStockReservationCommand(envelope, command));
            }
            case RELEASE_STOCK_RESERVATION_COMMAND -> {
                ReleaseStockReservationCommand command =
                        consumerUtil.deserialize(envelope.payload(), ReleaseStockReservationCommand.class);
                stockMetrics.record(reservationService.handleReleaseStockReservationCommand(envelope, command));
            }

        }
    }

}
